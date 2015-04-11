(ns lambdacd.steps.support
  (:require [clojure.string :as s]))

(defn merge-values [a b]
  (cond
    (and (map? a) (map? b))
      (merge a b)
    (and (string? a) (string? b))
      (s/join "\n" [a b])
    :default b))


(defn execute-until-failure [args ctx fns]
  (loop [x (first fns)
         rest (rest fns)
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
  `(fn [args# ctx#] ~form))

(defmacro chain [& forms]
  (let [fns (into [] (map to-fn forms))]
    `(execute-until-failure {} {} ~fns)))
