(ns lambdacd.steps.support
  (:require [clojure.string :as s]
            [clojure.core.async :as async]
            [lambdacd.internal.execution :as execution]
            [clojure.walk :as walk])
  (:import (java.io PrintWriter Writer StringWriter PrintStream)
           (org.apache.commons.io.output WriterOutputStream)))

(defn merge-values [a b]
  (cond
    (and (map? a) (map? b))
      (merge a b)
    (and (string? a) (string? b))
      (s/join "\n" [a b])
    :default b))


(defn- do-chain-steps [args ctx steps]
  "run the given steps one by one until a step fails and merge the results.
   results of one step are the inputs for the next one."
  (loop [x (first steps)
         rest (rest steps)
         result {}
         args args]
    (if (nil? x)
      result
      (let [step-result (x args ctx)
            complete-result (merge-with merge-values result step-result)
            next-args (merge args complete-result)]
        (if (and
              (not= :success (:status step-result))
              (not= nil step-result))
          complete-result
          (recur (first rest) (next rest) complete-result next-args))))))

(defn chain-steps
  ([args ctx & steps] ; a vector as single steps parameter is now deprecated and will no longer be supported in future releases
   (let [is-not-vararg (vector? (first steps))]
     (if is-not-vararg
      (do-chain-steps args ctx (first steps))
      (do-chain-steps args ctx steps)))))


(defn to-fn [form]
  (let [f# (first form)
        r# (next form)]
    (if (map? form)
      `(fn [& _# ] ~form)
      `(fn [args# ctx#] (~f# args# ctx# ~@r#)))))

;; Placeholders where args and ctx are injected by the chaining-macro.
(def injected-args)
(def injected-ctx)

(defn replace-args-and-ctx [args ctx]
  (fn [x]
    (case x
      injected-args args
      injected-ctx ctx
      x)))

(defn to-fn-with-args [form]
  (let [f# (first form)
        ctx (gensym "ctx")
        args (gensym "args")
        r# (walk/postwalk
             (replace-args-and-ctx args ctx)
             (next form))]
    (if (map? form)
      `(fn [& _# ] ~form)
      `(fn [~args ~ctx] (~f# ~@r#)))))

(defmacro chain [args ctx & forms]
  "DEPRECATED: USE chaining instead"
  (let [fns (vec (map to-fn forms))]
    `(apply chain-steps ~args ~ctx ~fns)))

(defmacro chaining [args ctx & forms]
  "a bit of syntactic sugar for chaining steps. Basically the threading-macro for LambdaCD. replaces :args and :ctx in calls"
  (let [fns (vec (map to-fn-with-args forms))]
    `(apply chain-steps ~args ~ctx ~fns)))

(defn- append-output [msg]
  (fn [old-output]
    (str old-output msg "\n")))

(defn new-printer []
  (atom ""))

(defn set-output [ctx msg]
  (async/>!! (:result-channel ctx) [:out msg]))

(defn print-to-output [ctx printer msg]
  (let [new-out (swap! printer (append-output msg))]
    (set-output ctx new-out)))

(defn printed-output [printer]
  @printer)


(defn killed? [ctx]
  @(:is-killed ctx))

(defmacro if-not-killed [ctx & body]
  `(if (killed? ~ctx)
     (do
       (async/>!! (:result-channel ~ctx) [:status :killed])
       {:status :killed})
     ~@body))

(defn merge-globals [step-results]
  (or (:global (reduce execution/keep-globals {} step-results)) {}))

(defn merge-step-results [step-results]
  (reduce execution/merge-two-step-results {} step-results))

; not part of the public interface, just public for the macro
(defn writer-to-ctx [ctx]
  (let [buf (StringWriter.)]
    {:writer (proxy [Writer] []
              (write [& [x ^Integer off ^Integer len]]
                (cond
                  (number? x) (.append buf (char x))
                  (not off) (.append buf x)
                  ; the CharSequence overload of append takes an *end* idx, not length!
                  (instance? CharSequence x) (.append buf ^CharSequence x (int off) (int (+ len off)))
                  :else (do
                          (.append buf (String. ^chars x) off len))))
              (flush []
                (set-output ctx (.toString (.getBuffer buf)))))
     :buffer (.getBuffer buf)}))

(defmacro capture-output [ctx & body]
  `(let [{x#      :writer
          buffer# :buffer} (writer-to-ctx ~ctx)
         body-result# (binding [*out* x#]
                        (do ~@body))]
     (if (associative? body-result#)
       (update body-result# :out #(if (nil? %) (str buffer#) (str buffer# "\n" % ))))))