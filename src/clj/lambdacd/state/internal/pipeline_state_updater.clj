(ns lambdacd.state.internal.pipeline-state-updater
  (:require [clojure.tools.logging :as log]
            [clojure.core.async :as async]
            [lambdacd.event-bus :as event-bus]
            [lambdacd.state.core :as state]
            [lambdacd.util :as util]))

(defn start-pipeline-state-updater [ctx]
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
            (state/consume-step-result-update ctx build-number step-id step-result)
            (event-bus/publish! ctx :step-result-update-consumed step-result-update)
            (recur)))))))

(defn stop-pipeline-state-updater [ctx]
  (log/info "Shutting down pipeline state updater...")
  (event-bus/publish!! ctx :stop-pipeline-state-updater {})
  (async/<!! (:pipeline-state-updater ctx))
  (log/info "Pipeline state updater stopped"))
