(ns lambdacd.execution
  "DEPRECATED, use lambdacd.execution.core instead"
  (:require [lambdacd.execution.core :as execution-core]))

(defn retrigger
  "DEPRECATED, use lambdacd.execution.core/retrigger-pipeline-async instead"
  [pipeline context build-number step-id-to-retrigger]
  (execution-core/retrigger-pipeline-async pipeline context build-number step-id-to-retrigger))

(defn kill-step [ctx build-number step-id]
  "DEPRECATED, use lambdacd.execution.core/kill-step instead"
  (execution-core/kill-step ctx build-number step-id))

(defn execute-steps [steps args ctx & opts]
  "DEPRECATED, use lambdacd.execution.core/execute-steps instead"
  (apply execution-core/execute-steps steps args ctx opts))

(defn execute-step
  ([args ctx step]
   "DEPRECATED, use lambdacd.execution.core/execute-steps instead"
   (execution-core/execute-step args [ctx step]))
  ([args [ctx step]]
   "DEPRECATED, use lambdacd.execution.core/execute-steps instead"
   (execution-core/execute-step args [ctx step])))

(defn run
  "DEPRECATED, use lambdacd.execution.core/run-pipeline instead"
  [pipeline context]
  (execution-core/run-pipeline pipeline context))


