(ns lambdacd.core
  (:use compojure.core)
  (:require [lambdacd.internal.pipeline-state :as pipeline-state]
            [lambdacd.internal.execution :as execution]))

(defn assemble-pipeline [pipeline-def config]
  (let [state (atom (pipeline-state/initial-pipeline-state config))
        context {:_pipeline-state state
                 :config config}]
    {:state state
     :context context
     :pipeline-def pipeline-def}))

(defn retrigger [pipeline context build-number step-id-to-retrigger]
  (execution/retrigger pipeline context build-number step-id-to-retrigger))

(defn execute-steps [steps args ctx & opts]
  (apply execution/do-execute-steps steps args ctx opts))

(defn execute-step [args [ctx step] & opts]
  (apply execution/execute-step args [ctx step] opts))

(defn new-base-context-for [ctx]
  (execution/new-base-context-for ctx))