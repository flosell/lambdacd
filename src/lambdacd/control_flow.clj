(ns lambdacd.control-flow
  "control flow elements for a pipeline: steps that control the way their child-steps are being run"
  (:require [lambdacd.execution :as execution]))

(defn- parallel-step-result-producer [args s-with-id]
  (pmap (partial execution/execute-step args) s-with-id))

(defn- execute-steps-in-parallel [steps args step-id]
  (execution/execute-steps parallel-step-result-producer steps args step-id))

(defn ^{:display-type :parallel} in-parallel [& steps]
  (fn [args step-id]
    (execute-steps-in-parallel steps args (cons 0 step-id))))



(defn ^{:display-type :container} in-cwd [cwd & steps]
  (fn [args step-id]
    (execution/execute-steps steps (assoc args :cwd cwd) (execution/new-base-id-for step-id))))
