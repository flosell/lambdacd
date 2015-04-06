(ns lambdacd.core
  (:use compojure.core)
  (:require [lambdacd.internal.pipeline-state :as pipeline-state]))

(defn assemble-pipeline [pipeline-def config]
  (let [state (atom (pipeline-state/initial-pipeline-state config))
        context {:_pipeline-state state
                 :config config}]
    {:state state
     :context context
     :pipeline-def pipeline-def}))
