(ns lambdacd.core
  (:use compojure.core)
  (:require [lambdacd.internal.default-pipeline-state :as default-pipeline-state]
            [lambdacd.internal.execution :as execution]
            [lambdacd.event-bus :as event-bus]
            [clojure.core.async :as async]))

(defn assemble-pipeline [pipeline-def config]
  (let [state (atom (default-pipeline-state/initial-pipeline-state config))
        step-results-channel (async/chan)
        pipeline-state-component (default-pipeline-state/new-default-pipeline-state state config step-results-channel)
        context (-> {:config                   config
                     :step-results-channel     step-results-channel
                     :pipeline-state-component pipeline-state-component}
                    (event-bus/initialize-event-bus))]
    {:state        state
     :context      context
     :pipeline-def pipeline-def}))

(defn retrigger [pipeline context build-number step-id-to-retrigger]
  (execution/retrigger-async pipeline context build-number step-id-to-retrigger))

(defn kill-step [ctx build-number step-id]
  (execution/kill-step ctx build-number step-id))

(defn execute-steps [steps args ctx & opts]
  (apply execution/execute-steps steps args ctx opts))

(defn execute-step [args ctx-and-step]
  (execution/execute-step args ctx-and-step))
