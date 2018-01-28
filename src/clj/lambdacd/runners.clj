(ns lambdacd.runners
  "Runners are what keeps a pipeline going. The start new builds based on some logic,
   e.g. when the previous build is finished or (e.g if the first step is a trigger) after the first step is done."
  (:require [lambdacd.execution.core :as execution]
            [clojure.core.async :as async]
            [lambdacd.event-bus :as event-bus]
            [clojure.tools.logging :as log]))

(defn- is-first-step? [step-finished-msg]
  (= (:step-id step-finished-msg) [1]))

(defn- is-not-retriggered? [step-finished-msg]
  (and
    (not (:retrigger-mock-for-build-number (:final-result step-finished-msg)))
    (not (:rerun-for-retrigger step-finished-msg))))

(def ^:private should-trigger-next-build?
  (every-pred is-first-step? is-not-retriggered?))

(defmacro while-not-stopped
  "Execute a runners body until it is stopped from the outside (usually by someone calling `stop-runner`, e.g. on shutdown)."
  [ctx & body]
  `(let [running# (atom true)]
     (async/go
       (async/<! (event-bus/subscribe ~ctx :stop-runner))
       (reset! running# false))
     (while @running#
       ~@body)
     (log/info "Runner stopped. No new pipeline instances will be spawned.")))

(defn start-new-run-after-first-step-finished
  "Runner that makes sure there is always one pipeline-run with the first step active. Useful if the first step is waiting for
  a trigger such as a VCS commit and you don't want to wait for the whole pipeline to complete before starting the next run.
  Do not use this if your first step finishes immediately as this will lead to lots of active pipelines"
  [{pipeline-def :pipeline-def context :context}]
  (let [subscription         (event-bus/subscribe context :step-finished)
        steps-finished       (event-bus/only-payload subscription)
        first-steps-finisehd (async/filter< should-trigger-next-build? steps-finished)
        pipeline-run-fn      (fn [] (async/thread (execution/run-pipeline pipeline-def context)))]
    (async/thread
      (while-not-stopped context
        (pipeline-run-fn)
        (async/<!! first-steps-finisehd)))))

(defn start-one-run-after-another
  "Runner that always keeps one pipeline-run active. It waits for a run to complete, then starts a new one.
  Commonly used when running multiple pipelines in parallel is not an option or the pipeline has no trigger."
  [{pipeline-def :pipeline-def context :context}]
 (async/thread
   (while-not-stopped context
     (execution/run-pipeline pipeline-def context))))

(defn stop-runner
  "Triggers an event that will stop runners so they no longer start new pipeline-runs.
  Does not stop running pipelines and does not wait for runners to stop."
  [ctx]
  (log/info "Stopping runner...")
  (event-bus/publish!! ctx :stop-runner {}))
