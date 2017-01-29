(ns lambdacd.execution.internal.pipeline
  (:require [clojure.core.async :as async]
            [lambdacd.execution.internal.execute-steps :as execute-steps]
            [lambdacd.presentation.pipeline-structure :as pipeline-structure]
            [lambdacd.execution.internal.build-metadata :as build-metadata]
            [lambdacd.state.core :as state]
            [lambdacd.event-bus :as event-bus]))

(defn run-pipeline [pipeline ctx build-number]
  (let [runnable-pipeline (map eval pipeline)]
    (state/consume-pipeline-structure ctx build-number (pipeline-structure/pipeline-display-representation pipeline))
    (event-bus/publish!! ctx :pipeline-started {:build-number build-number})
    (let [full-result (execute-steps/execute-steps runnable-pipeline {} (-> ctx
                                                          (assoc :result-channel (async/chan (async/dropping-buffer 0)))
                                                          (assoc :build-number build-number)
                                                          (assoc :step-id [])
                                                          (build-metadata/add-metadata-atom)))]
      (event-bus/publish!! ctx :pipeline-finished (assoc full-result :build-number build-number))
      full-result)))
