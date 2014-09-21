(ns lambdacd.core
  (:require [lambdacd.server :as server]
            [clojure.core.async :as async]
            [lambdacd.execution :as execution]))


(defn- start-pipeline-thread [pipeline-def]
  (async/thread (while true (execution/run pipeline-def))))

(defn mk-pipeline [pipeline-def]
  {:ring-handler (server/ui-for pipeline-def)
   :init (partial start-pipeline-thread pipeline-def)})