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


(defn- merge-step-status [a b]
  (cond
    (and (= :success a)
         (not= :success b)) b
    (and (not= :success a)
         (= :success b)) a
    :default b))

(defn- merge-step-results-failures-win [a b]
  (let [merged (merge-with merge-values a b)]
    (if (and (contains? a :status)
             (contains? b :status))
      (assoc merged :status (merge-step-status (:status a) (:status b)))
      merged)))

(defn- do-chain-steps [stop-on-step-failure args ctx steps]
  "run the given steps one by one until a step fails and merge the results.
   results of one step are the inputs for the next one."
  (loop [x (first steps)
         rest (rest steps)
         result {}
         args args]
    (if (nil? x)
      result
      (let [step-result     (x args ctx)
            complete-result (merge-step-results-failures-win result step-result)
            next-args       (merge args complete-result)
            step-failed     (and
                              (not= :success (:status step-result))
                              (not= nil step-result))]
        (if (and stop-on-step-failure step-failed)
          complete-result
          (recur (first rest) (next rest) complete-result next-args))))))

(defn always-chain-steps
  ([args ctx & steps]
   (do-chain-steps false args ctx steps)))

(defn chain-steps
  ([args ctx & steps]
   (do-chain-steps true args ctx steps)))


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
    (cond
      (and
        (symbol? x)
        (= (var injected-args) (resolve x))) args
      (and
        (symbol? x)
        (= (var injected-ctx) (resolve x))) ctx
      :else x)))

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