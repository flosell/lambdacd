(ns lambdacd.internal.execution
  "low level functions for job-execution"
  (:require [clojure.core.async :as async]
            [lambdacd.state.core :as state]
            [clojure.tools.logging :as log]
            [lambdacd.event-bus :as event-bus]
            [lambdacd.presentation.pipeline-structure :as pipeline-structure]
            [lambdacd.execution.internal.execute-steps :as execute-steps]))

(defn- run-with-build-number [pipeline ctx build-number]
  (let [runnable-pipeline (map eval pipeline)]
    (state/consume-pipeline-structure ctx build-number (pipeline-structure/pipeline-display-representation pipeline))
    (execute-steps/execute-steps runnable-pipeline {} (assoc ctx :result-channel (async/chan (async/dropping-buffer 0))
                                                                 :step-id []
                                                                 :build-number build-number))))

(defn run [pipeline ctx]
  (run-with-build-number pipeline ctx (state/next-build-number ctx)))

(defn retrigger [pipeline context build-number step-id-to-run next-build-number]
  (let [new-ctx (assoc context :retriggered-build-number build-number
                               :retriggered-step-id step-id-to-run)]
    (run-with-build-number pipeline new-ctx next-build-number)))

(defn retrigger-async [pipeline context build-number step-id-to-run]
  (let [next-build-number (state/next-build-number context)]
    (async/thread
      (retrigger pipeline context build-number step-id-to-run next-build-number))
    next-build-number))
