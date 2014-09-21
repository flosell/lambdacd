(ns lambdacd.core
  (:require [lambdacd.server :as server]
            [clojure.core.async :as async]
            [lambdacd.execution :as execution]
            [lambdacd.pipeline-state :as pipeline-state]))

(defn initial-context []
  {:_pipeline-state (atom pipeline-state/initial-pipeline-state)})

(defn- start-pipeline-thread [pipeline-def context]
  (async/thread (while true (execution/run pipeline-def context))))

(defn mk-pipeline [pipeline-def]
  (let [context (initial-context)
        state (:_pipeline-state context)]
    {:ring-handler (server/ui-for pipeline-def state)
     :init (partial start-pipeline-thread pipeline-def context)}))