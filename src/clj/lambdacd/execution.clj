(ns ^{:no-doc true
      :deprecated "0.13.0"} lambdacd.execution
  "DEPRECATED, use `lambdacd.execution.core` instead"
  (:require [lambdacd.execution.core :as execution-core]))

(defn retrigger
  "DEPRECATED, use `lambdacd.execution.core/retrigger-pipeline-async` instead"
  {:deprecated "0.13.0"}
  [pipeline context build-number step-id-to-retrigger]
  (execution-core/retrigger-pipeline-async pipeline context build-number step-id-to-retrigger))

(defn kill-step
  "DEPRECATED, use `lambdacd.execution.core/kill-step` instead"
  {:deprecated "0.13.0"}
  [ctx build-number step-id]
  (execution-core/kill-step ctx build-number step-id))

(defn execute-steps
  "DEPRECATED, use `lambdacd.execution.core/execute-steps` instead"
  {:deprecated "0.13.0"}
  [steps args ctx & opts]
  (apply execution-core/execute-steps steps args ctx opts))

(defn execute-step
  "DEPRECATED, use `lambdacd.execution.core/execute-steps` instead"
  {:deprecated "0.13.0"}
  ([args ctx step]
   (execution-core/execute-step args [ctx step]))
  ([args [ctx step]]
   (execution-core/execute-step args [ctx step])))

(defn run
  "DEPRECATED, use `lambdacd.execution.core/run-pipeline` instead"
  {:deprecated "0.13.0"}
  [pipeline context]
  (execution-core/run-pipeline pipeline context))
