(ns lambdacd.state.internal.pipeline-state-updater
  (:refer-clojure :exclude [update])
  (:require [clojure.tools.logging :as log]
            [clojure.core.async :as async]
            [lambdacd.event-bus :as event-bus]
            [lambdacd.internal.pipeline-state :refer [update]]
            [lambdacd.util :as util]))

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
