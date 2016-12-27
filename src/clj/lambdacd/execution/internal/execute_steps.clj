(ns lambdacd.execution.internal.execute-steps
  (:require [lambdacd.event-bus :as event-bus]
            [clojure.core.async :as async]
            [lambdacd.steps.status :as status]
            [lambdacd.util.internal.sugar :refer [not-nil?]]
            [lambdacd.execution.internal.serial-step-result-producer :refer [serial-step-result-producer]]
            [lambdacd.step-id :as step-id]
            [lambdacd.state.core :as state]
            [lambdacd.execution.internal.util :as execution-util]))

(defn- sequential-retrigger-predicate [ctx step]
  (let [cur-step-id         (:step-id ctx)
        retriggered-step-id (:retriggered-step-id ctx)]
    (cond
      (or
        (step-id/parent-of? cur-step-id retriggered-step-id)
        (= cur-step-id retriggered-step-id)) :rerun
      (step-id/later-than? cur-step-id retriggered-step-id) :run
      :else :mock)))

(defn unify-only-status [unify-status-fn]
  (fn [step-results]
    {:status (unify-status-fn (->> step-results
                                   (vals)
                                   (map :status)))}))

(defn- inherit-message-from-parent? [parent-ctx]
  (fn [msg]
    (let [msg-step-id          (:step-id msg)
          parent-step-id       (:step-id parent-ctx)
          msg-build            (:build-number msg)
          parent-build         (:build-number parent-ctx)
          msg-from-child?      (step-id/direct-parent-of? parent-step-id msg-step-id)
          msg-from-same-build? (= parent-build msg-build)]
      (and msg-from-child? msg-from-same-build?))))


(defn- to-context-and-step [ctx]
  (fn [idx step]
    (let [parent-step-id (:step-id ctx)
          new-step-id    (step-id/child-id parent-step-id (inc idx))
          step-ctx       (assoc ctx :step-id new-step-id)]
      [step-ctx step])))

(defn contexts-for-steps
  "creates contexts for steps"
  [steps base-context]
  (map-indexed (to-context-and-step base-context) steps))

(defn- process-inheritance [out-ch step-results-channel unify-results-fn]
  (async/go
    (let [dropping-output-ch (async/chan (async/sliding-buffer 1))]
      (async/pipe dropping-output-ch out-ch)
      (loop [results {}]
        (if-let [{step-id     :step-id
                  step-result :step-result} (async/<! step-results-channel)]
          (let [new-results (assoc results step-id step-result)
                old-unified (unify-results-fn results)
                new-unified (unify-results-fn new-results)]
            (if (not= old-unified new-unified)
              (async/>! dropping-output-ch new-unified))
            (recur new-results))
          (async/close! dropping-output-ch))))))


(defn- publish-child-step-results!! [ctx retriggered-build-number original-build-result]
  (->> original-build-result
       (filter #(step-id/parent-of? (:step-id ctx) (first %)))
       (map #(execution-util/send-step-result!! (assoc ctx :step-id (first %)) (assoc (second %) :retrigger-mock-for-build-number retriggered-build-number)))
       (doall)))

(defn retrigger-mock-step [retriggered-build-number]
  (fn [args ctx]
    (let [original-build-result (state/get-step-results ctx retriggered-build-number)
          original-step-result  (get original-build-result (:step-id ctx))]
      (publish-child-step-results!! ctx retriggered-build-number original-build-result)
      (assoc original-step-result
        :retrigger-mock-for-build-number retriggered-build-number))))

(defn- clear-retrigger-data [ctx]
  (assoc ctx
    :retriggered-build-number nil
    :retriggered-step-id nil))

(defn- replace-step-with-retrigger-mock [retrigger-predicate [ctx step]]
  (let [retriggered-build-number (:retriggered-build-number ctx)]
    (case (retrigger-predicate ctx step)
      :rerun [ctx step]
      :run [(clear-retrigger-data ctx) step]
      :mock [ctx (retrigger-mock-step retriggered-build-number)])))

(defn- add-retrigger-mocks [retrigger-predicate root-ctx step-contexts]
  (if (:retriggered-build-number root-ctx)
    (map (partial replace-step-with-retrigger-mock retrigger-predicate) step-contexts)
    step-contexts))

; TODO: this should be in a namespace like lambdacd.execution.core?
(defn execute-steps [steps args ctx & {:keys [step-result-producer is-killed unify-status-fn unify-results-fn retrigger-predicate]
                                       :or   {step-result-producer (serial-step-result-producer)
                                              is-killed            (atom false)
                                              ; unify-status-fn is DEPRECATED since 0.9.4
                                              unify-status-fn      status/successful-when-all-successful
                                              unify-results-fn     nil ; dependent on unify-status-fn, can't have it here for now
                                              retrigger-predicate  sequential-retrigger-predicate}}]
  (let [unify-results-fn                   (or unify-results-fn (unify-only-status unify-status-fn))
        steps                              (filter not-nil? steps)
        base-ctx-with-kill-switch          (assoc ctx :is-killed is-killed)
        subscription                       (event-bus/subscribe ctx :step-result-updated)
        children-step-results-channel      (->> subscription
                                                (event-bus/only-payload)
                                                (async/filter< (inherit-message-from-parent? ctx)))
        step-contexts                      (contexts-for-steps steps base-ctx-with-kill-switch)
        _                                  (process-inheritance (:result-channel ctx) children-step-results-channel unify-results-fn)
        step-contexts-with-retrigger-mocks (add-retrigger-mocks retrigger-predicate ctx step-contexts)
        step-results                       (step-result-producer args step-contexts-with-retrigger-mocks)
        result                             (reduce execution-util/merge-two-step-results step-results)]
    (event-bus/unsubscribe ctx :step-result-updated subscription)
    result))
