(ns lambdacd.internal.execution
  "low level functions for job-execution"
  (:require [clojure.core.async :as async]
            [lambdacd.state.core :as state]
            [clojure.tools.logging :as log]
            [lambdacd.event-bus :as event-bus]
            [lambdacd.presentation.pipeline-structure :as pipeline-structure]
            [lambdacd.execution.internal.execute-steps :as execute-steps]))

(defn run [pipeline context]
  (let [build-number (state/next-build-number context)]
    (state/consume-pipeline-structure context build-number (pipeline-structure/pipeline-display-representation pipeline))
    (let [runnable-pipeline (map eval pipeline)]
      (execute-steps/execute-steps runnable-pipeline {} (merge context {:result-channel (async/chan (async/dropping-buffer 0))
                                                          :step-id                      []
                                                          :build-number                 build-number})))))

(defn retrigger [pipeline context build-number step-id-to-run next-build-number]
  (let [executable-pipeline (map eval pipeline)]
    (state/consume-pipeline-structure context next-build-number (pipeline-structure/pipeline-display-representation pipeline))
    (execute-steps/execute-steps executable-pipeline {} (assoc context :step-id []
                                                         :result-channel (async/chan (async/dropping-buffer 0))
                                                         :build-number next-build-number
                                                         :retriggered-build-number build-number
                                                         :retriggered-step-id step-id-to-run))))

(defn retrigger-async [pipeline context build-number step-id-to-run]
  (let [next-build-number (state/next-build-number context)]
    (async/thread
      (retrigger pipeline context build-number step-id-to-run next-build-number))
    next-build-number))

(defn kill-step [ctx build-number step-id]
  (event-bus/publish!! ctx :kill-step {:step-id      step-id
                                       :build-number build-number}))

(defn- timed-out [ctx start-time]
  (let [now        (System/currentTimeMillis)
        ms-elapsed (- now start-time)
        timeout    (:ms-to-wait-for-shutdown (:config ctx))
        result     (> ms-elapsed timeout)]
    (if result
      (log/warn "Waiting for pipelines to complete timed out after" timeout "ms! Most likely a build step did not react quickly to kill signals"))
    result))

(defn- wait-for-pipelines-to-complete [ctx]
  (let [start-time (System/currentTimeMillis)]
    (while (and
             (not-empty @(:started-steps ctx))
             (not (timed-out ctx start-time)))
      (log/debug "Waiting for steps to complete:" @(:started-steps ctx))
      (Thread/sleep 100))))

(defn kill-all-pipelines [ctx]
  (log/info "Killing all running pipelines...")
  (event-bus/publish!! ctx :kill-step {:step-id      :any-root
                                       :build-number :any})
  (wait-for-pipelines-to-complete ctx))
