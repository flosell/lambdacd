(ns lambdacd.runners
  (:require [lambdacd.internal.execution :as execution]
            [clojure.core.async :as async]
            [lambdacd.internal.pipeline-state :as pipeline-state]))

(defn start-new-run-after-first-step-finished [{pipeline-def :pipeline-def context :context}]
 (let [pipeline-run-fn (fn [] (async/thread (execution/run pipeline-def context)))]
   (pipeline-state/notify-when-no-first-step-is-active context pipeline-run-fn)
   (pipeline-run-fn)))

(defn start-one-run-after-another [{pipeline-def :pipeline-def context :context}]
 (async/thread
   (while true
     (execution/run pipeline-def context))))