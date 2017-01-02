(ns lambdacd.execution.internal.kill
  (:require [lambdacd.event-bus :as event-bus]
            [clojure.core.async :as async]
            [clojure.tools.logging :as log])
  (:import (java.util UUID)))

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

(defn wrap-execute-step-with-kill-handling [handler]
  (fn [args ctx]
    (let [child-kill-switch  (atom false)
          parent-kill-switch (:is-killed ctx)
          watch-key          (UUID/randomUUID)
          _                  (add-watch parent-kill-switch watch-key (fn [key reference old new] (reset! child-kill-switch new)))
          _                  (reset! child-kill-switch @parent-kill-switch) ; make sure kill switch has the parents state in the beginning and is updated through the watch
          ctx-for-child      (assoc ctx :is-killed child-kill-switch)
          _                  (add-kill-switch-reporter ctx-for-child)
          kill-subscription  (kill-step-handling ctx-for-child)
          handler-result     (handler args ctx-for-child)]
      (clean-up-kill-handling ctx-for-child kill-subscription)
      (remove-watch parent-kill-switch watch-key)
      handler-result)))

; ============================================

(defn kill-step [ctx build-number step-id]
  (event-bus/publish!! ctx :kill-step {:step-id      step-id
                                       :build-number build-number}))

; ============================================

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

; ============================================

(defn- add-kill-switch [is-killed]
  (fn [[ctx step]]
    [(assoc ctx :is-killed is-killed) step]))

(defn wrap-execute-steps-with-kill-handling [handler is-killed]
  (fn [step-contexts-and-steps args ctx]
    (handler (map (add-kill-switch is-killed) step-contexts-and-steps)
             args
             ctx)))
