(ns lambdacd.core
  (:use compojure.core)
  (:require [lambdacd.internal.default-pipeline-state :as pipeline-state]
            [lambdacd.internal.execution :as execution]
            [clojure.core.async :as async]))

(defn assemble-pipeline [pipeline-def config]
  (let [state (atom (pipeline-state/initial-pipeline-state config))
        context {:_pipeline-state      state
                 :config               config
                 :step-results-channel (async/chan)}
        pipeline-state-component (pipeline-state/new-default-pipeline-state state config context)]
    {:state state
     :context context
     :pipeline-state-component pipeline-state-component
     :pipeline-def pipeline-def}))

(defn retrigger [pipeline context build-number step-id-to-retrigger]
  (execution/retrigger-async pipeline context build-number step-id-to-retrigger))

(defn execute-steps [steps args ctx & opts]
  (apply execution/execute-steps steps args ctx opts))

(defn execute-step [args ctx-and-step]
  (execution/execute-step args ctx-and-step))