(ns lambdacd.control-flow
  (:require [lambdacd.dsl :as dsl]))

(defn parallel-step-result-producer [args s-with-id]
  (pmap (partial dsl/execute-step args) s-with-id))

(defn execute-steps-in-parallel [steps args step-id]
  (dsl/execute-steps parallel-step-result-producer steps args step-id))

(defn ^{:display-type :parallel} in-parallel [& steps]
  (fn [args step-id]
    (execute-steps-in-parallel steps args (cons 0 step-id))))

(defn ^{:display-type :container} in-cwd [cwd & steps]
  (fn [args step-id]
    (dsl/execute-steps steps (assoc args :cwd cwd) (dsl/new-base-id-for step-id))))
