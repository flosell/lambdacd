(ns lambdacd.core
  (:require [lambdacd.server :as server]
            [clojure.core.async :as async]
            [lambdacd.execution :as execution]
            [lambdacd.pipeline-state :as pipeline-state]))


(defn- start-pipeline-thread [pipeline-def context]
  (async/thread (while true (execution/run pipeline-def context))))

(defn mk-pipeline [pipeline-def]
  (let [state (atom pipeline-state/initial-pipeline-state)
        context {:_pipeline-state state}]
    {:ring-handler (server/ui-for pipeline-def state)
     :init (partial start-pipeline-thread pipeline-def context)}))