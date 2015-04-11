(ns lambdacd.steps.support
  (:require [clojure.string :as s]))

(defn merge-values [a b]
  (cond
    (and (map? a) (map? b))
      (merge a b)
    (and (string? a) (string? b))
      (s/join "\n" [a b])
    :default b))


(defn chain-steps [args ctx steps]
  "run the given steps one by one until a step fails and merge the results.
   results of one step are the inputs for the next one."
  (loop [x (first steps)
         rest (rest steps)
         result {}
         args args]
    (if (nil? x)
      result
      (let [step-result (x args ctx)
            complete-result (merge-with merge-values result step-result)]
        (if (not= :success (:status step-result))
          complete-result
          (recur (first rest) (next rest) complete-result complete-result))))))

(defn to-fn [form]
  (let [f# (first form)
        r# (next form)]
    `(fn [args# ctx#] (~f# args# ctx# ~@r#))))

(defmacro chain [args ctx & forms]
  "a bit of syntactic sugar for chaining steps. Basically the threading-macro for LambdaCD"
  (let [fns (into [] (map to-fn forms))]
    `(chain-steps ~args ~ctx ~fns)))
