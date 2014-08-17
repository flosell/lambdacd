(ns lambdaci.dsl)

(defn merge-step-results [r1 r2]
  (merge-with vector r1 r2))


(defn execute-step [step args]
  (println "=============================")
  (println (str "input " args))
  (let [step-result (step args)]
    (println (str "output " step-result))
    step-result))


(defn execute-steps [steps args]
    (reduce #(merge-step-results %1 (execute-step %2 %1)) args steps))

(defn in-parallel [& steps]
  (fn [args]
    (let [step-futures (map #(future (execute-step %1 args)) steps)
          step-results (map deref step-futures)]
      (reduce #(merge-step-results %1 %2) step-results))))

(defn in-cwd [specified-working-directory & steps]
  (fn [_]
    (execute-steps steps {:cwd specified-working-directory })))


(defn run [pipeline]
  (let [runnable-pipeline (map eval pipeline)]
    (execute-steps runnable-pipeline {})))
