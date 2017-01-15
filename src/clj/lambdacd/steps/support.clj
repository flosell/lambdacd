(ns lambdacd.steps.support
  (:require [clojure.core.async :as async]
            [lambdacd.execution.internal.execute-steps :as execute-steps]
            [lambdacd.execution.internal.serial-step-result-producer :as serial-step-result-producer]
            [clojure.walk :as walk]
            [lambdacd.step-id :as step-id]
            [lambdacd.steps.result :as step-results]
            [lambdacd.execution.internal.util :as execution-util])
  (:import (java.io Writer StringWriter)))

(defn- merge-step-results-with-joined-output [a b]
  (step-results/merge-two-step-results a b :resolvers [step-results/status-resolver
                                                       step-results/merge-nested-maps-resolver
                                                       step-results/join-output-resolver
                                                       step-results/second-wins-resolver]))

(defn- wrap-step-to-allow-nil-values [step]
  (fn [args ctx]
    (let [result (step args ctx)]
      (if (nil? result)
        args
        result))))

(defn- step-results-sorted-by-id [outputs]
  (->> outputs
       (into (sorted-map-by step-id/before?))
       (vals)))

(defn unify-results [step-results]
  (-> step-results
      (step-results-sorted-by-id)
      (step-results/merge-step-results merge-step-results-with-joined-output)))

(defn- do-chain-steps-with-execute-steps [args ctx steps step-result-producer]
  (let [execute-step-result (execute-steps/execute-steps steps args ctx
                                                     :step-result-producer step-result-producer
                                                     :unify-results-fn unify-results)
        sorted-step-results (step-results-sorted-by-id (:outputs execute-step-result))
        merged-step-results (step-results/merge-step-results sorted-step-results merge-step-results-with-joined-output)]
    (merge merged-step-results execute-step-result)))

(defn chain-steps
  ([args ctx & steps]
   (do-chain-steps-with-execute-steps args ctx
                                      (map wrap-step-to-allow-nil-values steps)
                                      (serial-step-result-producer/serial-step-result-producer))))

(defn always-chain-steps
  ([args ctx & steps]
   (do-chain-steps-with-execute-steps args ctx
                                      (map wrap-step-to-allow-nil-values steps)
                                      (serial-step-result-producer/serial-step-result-producer :stop-predicate (constantly false)))))

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

(defn- do-chaining [chain-fn args ctx forms]
  (let [fns (vec (map to-fn-with-args forms))]
    `(apply ~chain-fn ~args ~ctx ~fns)))

(defmacro chaining [args ctx & forms]
  "syntactic sugar for chain-steps. can work with arbitrary code and can inject args and ctx"
  (do-chaining chain-steps args ctx forms))

(defmacro always-chaining [args ctx & forms]
  "syntactic sugar for always-chain-steps. can work with arbitrary code and can inject args and ctx"
  (do-chaining always-chain-steps args ctx forms))

(defn last-step-status-wins [step-result]
  (let [winning-status (->> step-result
                           :outputs
                           (sort-by #(vec (first %)))
                           last
                           second
                           :status)]
    (assoc step-result :status winning-status)))

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
  (or (:global (reduce execution-util/keep-globals {} step-results)) {}))

(defn merge-step-results [step-results]
  (reduce execution-util/merge-two-step-results {} step-results))

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

(defn unify-only-status
  "Converts a function that can unify statuses into a unify-results-fn suitable for execute-steps"
  [unify-status-fn]
  (execute-steps/unify-only-status unify-status-fn))


(defn assoc-metadata! [ctx & kvs]
  (swap! (:build-metadata-atom ctx) #(apply assoc % kvs)))
