(ns lambdacd.steps.support)


(defn execute-until-failure [fns]
  (loop [x (first fns)
         rest (rest fns)
         result {}]
    (if (nil? x)
      result
      (let [step-result (x)
            complete-result (merge result step-result)]
        (if (not= :success (:status step-result))
          complete-result
          (recur (first rest) (next rest) complete-result))))))

(defn to-fn [form]
  `(fn [] ~form))

(defmacro chain [& forms]
  (let [fns (into [] (map to-fn forms))]
    `(execute-until-failure ~fns)))
