(ns lambdacd.execution.internal.execute-step
  (:require [clojure.core.async :as async]
            [lambdacd.util.internal.exceptions :as util-exceptions]
            [clojure.tools.logging :as log]
            [lambdacd.event-bus :as event-bus]
            [lambdacd.execution.internal.util :as execution-util]
            [throttler.core :as throttler])
  (:import (java.util UUID)))

; ============================================

(defn- execute-or-catch [step args ctx]
  (try
    (let [step-result (step args ctx)]
      (if (nil? (:status step-result))
        {:status :failure :out "step did not return any status!"}
        step-result))
    (catch Exception e
      {:status :failure :out (util-exceptions/stacktrace-to-string e)})
    (finally
      (async/close! (:result-channel ctx)))))

(defn execute-step-main-handler [args [ctx step]]
  (let [immediate-step-result (execute-or-catch step args ctx)]
    immediate-step-result))

; ============================================

(defn wrap-execute-step-logging [handler]
  (fn [args [ctx step]]
    (let [step-id              (:step-id ctx)
          complete-step-result (handler args [ctx step])]
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

(defn- report-step-finished [ctx complete-step-result]
  (event-bus/publish!! ctx :step-finished {:step-id             (:step-id ctx)
                                           :build-number        (:build-number ctx)
                                           :final-result        complete-step-result
                                           :rerun-for-retrigger (boolean
                                                                  (and (:retriggered-build-number ctx)
                                                                       (:retriggered-step-id ctx)))}))

(defn- report-step-started [ctx]
  (execution-util/send-step-result!! ctx {:status :running})
  (event-bus/publish!! ctx :step-started {:step-id      (:step-id ctx)
                                          :build-number (:build-number ctx)}))

(defn- step-output [step-id step-result]
  {:outputs {step-id step-result}
   :status  (get step-result :status)})

(defn wrap-step-result-reporting [handler]
  (fn [args [ctx step]]
    (let [step-id                   (:step-id ctx)
          result-ch                 (async/chan)
          ctx-for-child             (assoc ctx :result-channel result-ch)
          processed-async-result-ch (process-channel-result-async result-ch ctx)
          _                         (report-step-started ctx)
          immediate-step-result     (handler args [ctx-for-child step])
          processed-async-result    (async/<!! processed-async-result-ch)
          complete-step-result      (merge processed-async-result immediate-step-result)]
      (execution-util/send-step-result!! ctx complete-step-result)
      (report-step-finished ctx complete-step-result)
      (step-output step-id complete-step-result))))

; ============================================


(defn- step-id-to-kill? [step-id kill-payload]
  (let [step-id-to-kill     (:step-id kill-payload)

        exact-step-id-match (= step-id step-id-to-kill)

        any-root-match      (and (= :any-root step-id-to-kill)
                                 (= 1 (count step-id)))]
    (or exact-step-id-match
        any-root-match)))

(defn- build-number-to-kill? [build-number kill-payload]
  (let [build-number-to-kill (:build-number kill-payload)]
    (or (= build-number build-number-to-kill)
        (= :any build-number-to-kill))))

(defn- kill-step-handling [ctx]
  (let [is-killed     (:is-killed ctx)
        step-id       (:step-id ctx)
        build-number  (:build-number ctx)
        subscription  (event-bus/subscribe ctx :kill-step)
        kill-payloads (event-bus/only-payload subscription)]
    (async/go-loop []
      (if-let [kill-payload (async/<! kill-payloads)]
        (if (and
              (step-id-to-kill? step-id kill-payload)
              (build-number-to-kill? build-number kill-payload))
          (reset! is-killed true)
          (recur))))
    subscription))

(defn- clean-up-kill-handling [ctx subscription]
  (event-bus/unsubscribe ctx :kill-step subscription))

(defn- report-received-kill [ctx]
  (async/>!! (:result-channel ctx) [:received-kill true]))

(defn- add-kill-switch-reporter [ctx]
  (add-watch (:is-killed ctx) (UUID/randomUUID) (fn [_ _ _ new-is-killed-val]
                                                  (if new-is-killed-val
                                                    (report-received-kill ctx)))))

(defn wrap-kill-handling [handler]
  (fn [args [ctx step]]
    (let [child-kill-switch  (atom false)
          parent-kill-switch (:is-killed ctx)
          watch-key          (UUID/randomUUID)
          _                  (add-watch parent-kill-switch watch-key (fn [key reference old new] (reset! child-kill-switch new)))
          _                  (reset! child-kill-switch @parent-kill-switch) ; make sure kill switch has the parents state in the beginning and is updated through the watch
          ctx-for-child      (assoc ctx :is-killed child-kill-switch)
          _                  (add-kill-switch-reporter ctx-for-child)
          kill-subscription  (kill-step-handling ctx-for-child)
          handler-result     (handler args [ctx-for-child step])]
      (clean-up-kill-handling ctx-for-child kill-subscription)
      (remove-watch parent-kill-switch watch-key)
      handler-result)))


(defn execute-step [args [ctx step]] ; TODO: this should be in a namespace like lambdacd.execution.core?
  (let [assembled-handler (-> execute-step-main-handler
                              (wrap-kill-handling)
                              (wrap-step-result-reporting)
                              (wrap-execute-step-logging))]
    (assembled-handler args [ctx step])))
