(ns lambdacd.steps.support
  (:require [clojure.string :as s]
            [clojure.core.async :as async]
            [lambdacd.internal.execution :as execution]
            [lambdacd.util :as util]))

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
        (if (not= :success (:status step-result))
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

(defmacro chain [args ctx & forms]
  "a bit of syntactic sugar for chaining steps. Basically the threading-macro for LambdaCD"
  (let [fns (vec (map to-fn forms))]
    `(apply chain-steps ~args ~ctx ~fns)))

(defn- append-output [msg]
  (fn [old-output]
    (str old-output msg "\n")))

(defn new-printer []
  (atom ""))

(defn print-to-output [ctx printer msg]
  (let [new-out (swap! printer (append-output msg))]
    (async/>!! (:result-channel ctx) [:out new-out])))

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