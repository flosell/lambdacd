(ns lambdacd.core
  (:use compojure.core)
  (:require [lambdacd.internal.default-pipeline-state :as default-pipeline-state]
            [lambdacd.internal.execution :as execution]
            [lambdacd.event-bus :as event-bus]
            [clojure.core.async :as async]))

(defn- initialize-default-pipeline-state [ctx]
  (let [config (:config ctx)
        state (atom (default-pipeline-state/initial-pipeline-state config))
        pipeline-state-component (default-pipeline-state/new-default-pipeline-state state config ctx)]
    (assoc ctx :pipeline-state-component pipeline-state-component
               :_state state)))

(defn assemble-pipeline [pipeline-def config]
  (let [context (-> {:config                   config
                     :step-results-channel     (async/chan (async/dropping-buffer 100))}
                    (event-bus/initialize-event-bus)
                    (initialize-default-pipeline-state))]
    {:state        (:_state context) ;; FIXME: this is only temporary, nothing should access the state directly but the ui does
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
