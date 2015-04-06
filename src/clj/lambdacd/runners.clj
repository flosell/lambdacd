(ns lambdacd.runners
  (:require [lambdacd.internal.execution :as execution]
            [clojure.core.async :as async]
            [lambdacd.internal.pipeline-state :as pipeline-state]))

(defn start-new-run-after-first-step-finished
  ([{pipeline-def :pipeline-def context :context}]
   (start-new-run-after-first-step-finished pipeline-def context))
  ([pipeline-def context]
   (let [pipeline-run-fn (fn [] (async/thread (execution/run pipeline-def context)))]
     (pipeline-state/notify-when-most-recent-build-running context pipeline-run-fn)
     (pipeline-run-fn))))

(defn start-one-run-after-another
  ([{pipeline-def :pipeline-def context :context}]
   (start-one-run-after-another pipeline-def context))
  ([pipeline-def context]
   (async/thread (while true (execution/run pipeline-def context)))))