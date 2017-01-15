(ns lambdacd.execution.internal.execute-step
  (:require [clojure.core.async :as async]
            [lambdacd.util.internal.exceptions :as util-exceptions]
            [clojure.tools.logging :as log]
            [lambdacd.event-bus :as event-bus]
            [lambdacd.execution.internal.util :as execution-util]
            [lambdacd.execution.internal.kill :as kill]
            [throttler.core :as throttler])
  (:import (java.util UUID)))

; ============================================

(defn wrap-failure-if-no-status [handler]
  (fn [args ctx]
    (let [step-result (handler args ctx)]
      (if (nil? (:status step-result))
        {:status :failure :out "step did not return any status!"}
        step-result))))

(defn wrap-close-result-channel [handler]
  (fn [args ctx]
    (let [result (handler args ctx)]
      (async/close! (:result-channel ctx))
      result)))

(defn wrap-exception-handling [handler]
  (fn [args ctx]
    (try
      (handler args ctx)
      (catch Exception e
        {:status :failure :out (util-exceptions/stacktrace-to-string e)}))))

; ============================================

(defn wrap-execute-step-logging [handler]
  (fn [args ctx]
    (let [step-id              (:step-id ctx)
          complete-step-result (handler args ctx)]
      (log/debug (str "executed step " step-id complete-step-result))
      complete-step-result)))

; ============================================

(defn- attach-wait-indicator-if-necessary [result k v]
  (if (and (= k :status) (= v :waiting))
    (assoc result :has-been-waiting true)
    result))

(defn- append-result [cur-result [key value]]
  (-> cur-result
      (assoc key value)
      (attach-wait-indicator-if-necessary key value)))

(defn- drop-and-throttle-ch [step-updates-per-sec in-ch]
  (let [dropping-ch (async/chan (async/sliding-buffer 1))
        slow-chan   (throttler/throttle-chan dropping-ch step-updates-per-sec :second)]
    (async/pipe in-ch dropping-ch)
    slow-chan))


(defn- publish-from-ch [ctx topic in-ch]
  (let [updates-per-sec (get-in ctx [:config :step-updates-per-sec])
        slow-chan       (if updates-per-sec
                          (drop-and-throttle-ch updates-per-sec in-ch)
                          in-ch)]
    (async/go-loop []
      (if-let [msg (async/<! slow-chan)]
        (do
          (event-bus/publish! ctx topic msg)
          (recur))))))

(defn- process-channel-result-async [c {step-id :step-id build-number :build-number :as ctx}]
  (let [publisher-ch       (async/chan)
        publisher-finished (publish-from-ch ctx :step-result-updated publisher-ch)
        processed-result   (async/go-loop [cur-result {:status :running}]
                             (let [ev         (async/<! c)
                                   new-result (if (map? ev)
                                                ev
                                                (append-result cur-result ev))]
                               (if (nil? ev)
                                 (do
                                   (async/close! publisher-ch)
                                   cur-result)
                                 (do
                                   (async/>! publisher-ch {:build-number build-number
                                                           :step-id      step-id
                                                           :step-result  new-result})
                                   (recur new-result)))))]
    (async/go
      (async/<! publisher-finished)
      (async/<! processed-result))))

(defn wrap-async-step-result-handling [handler]
  (fn [args ctx]
    (let [result-ch                 (async/chan)
          ctx-for-child             (assoc ctx :result-channel result-ch)
          processed-async-result-ch (process-channel-result-async result-ch ctx)
          immediate-step-result     (handler args ctx-for-child)
          processed-async-result    (async/<!! processed-async-result-ch)
          complete-step-result      (merge processed-async-result immediate-step-result)]
      complete-step-result)))

; ============================================

(defn- report-step-started [ctx]
  (execution-util/send-step-result!! ctx {:status :running})
  (event-bus/publish!! ctx :step-started {:step-id      (:step-id ctx)
                                          :build-number (:build-number ctx)}))

(defn- report-step-finished [ctx complete-step-result]
  (execution-util/send-step-result!! ctx complete-step-result)
  (event-bus/publish!! ctx :step-finished {:step-id             (:step-id ctx)
                                           :build-number        (:build-number ctx)
                                           :final-result        complete-step-result
                                           :rerun-for-retrigger (boolean
                                                                  (and (:retriggered-build-number ctx)
                                                                       (:retriggered-step-id ctx)))}))

(defn wrap-report-step-started [handler]
  (fn [args ctx]
    (report-step-started ctx)
    (handler args ctx)))

(defn wrap-report-step-stopped [handler]
  (fn [args ctx]
    (let [complete-step-result (handler args ctx)]
      (report-step-finished ctx complete-step-result)
      complete-step-result)))

; ============================================

(defn- step-output [step-id step-result]
  {:outputs {step-id step-result}
   :status  (get step-result :status)})

(defn wrap-convert-to-step-output [handler]
  (fn [args ctx]
    (step-output (:step-id ctx) (handler args ctx))))

; ============================================

(defn execute-step [args [ctx step]]                        ; TODO: this should be in a namespace like lambdacd.execution.core?
  (let [assembled-handler (-> step
                              (wrap-report-step-started)
                              (wrap-failure-if-no-status)
                              (wrap-exception-handling)
                              (kill/wrap-execute-step-with-kill-handling)
                              (wrap-close-result-channel)
                              (wrap-async-step-result-handling)
                              (wrap-report-step-stopped)
                              (wrap-execute-step-logging)
                              (wrap-convert-to-step-output))]
    (assembled-handler args ctx)))
