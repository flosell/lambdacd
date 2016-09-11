(ns lambdacd.internal.pipeline-state
  (:require [clojure.core.async :as async]
            [lambdacd.event-bus :as event-bus]
            [lambdacd.util :as util]
            [clojure.tools.logging :as log])
  (:refer-clojure :exclude [alias update]))


; TODO: move this protocol out of internal once the interface is more polished and stable
; in the meantime, you can use this protocol but keep in mind it's subject to change
(defprotocol PipelineStateComponent
  "components implementing this protocol can provide the state of a pipeline"
  (update            [self build-number step-id step-result])
  (get-all           [self]) ;; should in the future be replaced with more detailed accessor functions
  (get-internal-state [self]) ;; FIXME: temporary, hack until runners are rewritten to use step-results-channel
  (next-build-number [self]))

(defn start-pipeline-state-updater [pipeline-state ctx]
  (let [step-updates-channel (util/buffered
                               (event-bus/only-payload
                                 (event-bus/subscribe ctx :step-result-updated)))
        stop-updater-channel (event-bus/only-payload
                               (event-bus/subscribe ctx :stop-pipeline-state-updater))]
    (async/go-loop []
      (if-let [[step-result-update ch] (async/alts! [step-updates-channel stop-updater-channel])]
        (when (not= stop-updater-channel ch)
          (let [step-result  (:step-result step-result-update)
                build-number (:build-number step-result-update)
                step-id      (:step-id step-result-update)]
            (update pipeline-state build-number step-id step-result)
            (recur)))))))

(defn stop-pipeline-state-updater [ctx]
  (log/info "Shutting down pipeline state updater...")
  (event-bus/publish ctx :stop-pipeline-state-updater {})
  (async/<!! (:pipeline-state-updater ctx))
  (log/info "Pipeline state updater stopped"))
