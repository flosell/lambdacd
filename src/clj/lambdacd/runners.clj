(ns lambdacd.runners
  (:require [lambdacd.internal.execution :as execution]
            [clojure.core.async :as async]
            [lambdacd.internal.default-pipeline-state :as pipeline-state]))

(defn start-new-run-after-first-step-finished
  "Runner that makes sure there is always one pipeline-run with the first step active. useful if the first step is waiting for
  a trigger such as a VCS commit and you don't want to wait for the whole pipeline to complete before starting the next run.
  Do not use this if your first step finishes immediately as this will lead to lots of active pipelines"
  [{pipeline-def :pipeline-def context :context}]
 (let [pipeline-run-fn (fn [] (async/thread (execution/run pipeline-def context)))]
   (pipeline-state/notify-when-no-first-step-is-active context pipeline-run-fn)
   (pipeline-run-fn)))

(defn start-one-run-after-another
  "Runner that always keeps one pipeline-run active. It waits for a run to complete, then starts a new one."
  [{pipeline-def :pipeline-def context :context}]
 (async/thread
   (while true
     (execution/run pipeline-def context))))