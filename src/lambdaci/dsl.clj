(ns lambdaci.dsl)

(defn defbuild [& steps]
  {:steps steps})

(defn execute [& fns]
  fns)

(defn execute-step [step args]
  (println "=============================")
  (let [step-result (step args)]
    (println (str "input " args))
    (println (str "output " step-result))
    step-result))

(defn execute-steps [steps]
  (reduce #(merge %1 (execute-step %2 %1)) {} steps))

(defn inParallel [& fns]
  (fn [_] (execute-steps fns))) ; FIXME?


(defn run [pipeline]
  (execute-steps (:steps pipeline)))
