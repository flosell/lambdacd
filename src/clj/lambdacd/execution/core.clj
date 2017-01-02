(ns lambdacd.execution.core
  "low level functions for job-execution"
  (:require [clojure.core.async :as async]
            [lambdacd.state.core :as state]
            [lambdacd.execution.internal.pipeline :as pipeline]
            [lambdacd.execution.internal.execute-step :as execute-step]
            [lambdacd.execution.internal.execute-steps :as execute-steps]
            [lambdacd.execution.internal.kill :as kill]))

(defn run-pipeline [pipeline ctx]
  (pipeline/run-pipeline pipeline ctx (state/next-build-number ctx)))

(defn retrigger-pipeline [pipeline context build-number step-id-to-run next-build-number]
  (let [new-ctx (assoc context :retriggered-build-number build-number
                               :retriggered-step-id step-id-to-run)]
    (pipeline/run-pipeline pipeline new-ctx next-build-number)))

(defn retrigger-pipeline-async [pipeline context build-number step-id-to-run]
  (let [next-build-number (state/next-build-number context)]
    (async/thread
      (retrigger-pipeline pipeline context build-number step-id-to-run next-build-number))
    next-build-number))

(defn execute-step
  ([args ctx step]
   (execute-step/execute-step args [ctx step]))
  ([args [ctx step]]
   (execute-step/execute-step args [ctx step])))

(defn execute-steps [steps args ctx & opts]
  (apply execute-steps/execute-steps steps args ctx opts))

(defn kill-step [ctx build-number step-id]
  (kill/kill-step ctx build-number step-id))
