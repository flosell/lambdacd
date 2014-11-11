(ns lambdacd.core
  (:require [lambdacd.server :as server]
            [clojure.core.async :as async]
            [lambdacd.execution :as execution]
            [lambdacd.pipeline-state :as pipeline-state]))


(defn- start-pipeline-thread [pipeline-def context]
  (async/thread (while true (execution/run pipeline-def context))))

(defn mk-pipeline [pipeline-def config]
  (let [state (atom (pipeline-state/initial-pipeline-state config))
        context {:_pipeline-state state
                 :config config}]
    {:ring-handler (server/ui-for pipeline-def state)
     :init (partial start-pipeline-thread pipeline-def context)}))