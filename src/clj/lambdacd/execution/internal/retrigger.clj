(ns lambdacd.execution.internal.retrigger
  (:require [lambdacd.state.core :as state]
            [lambdacd.step-id :as step-id]
            [lambdacd.execution.internal.util :as execution-util]))


(defn- publish-child-step-results!! [ctx retriggered-build-number original-build-result]
  (->> original-build-result
       (filter #(step-id/parent-of? (:step-id ctx) (first %)))
       (map #(execution-util/send-step-result!! (assoc ctx :step-id (first %)) (assoc (second %) :retrigger-mock-for-build-number retriggered-build-number)))
       (doall)))

(defn sequential-retrigger-predicate [ctx step]
  (let [cur-step-id         (:step-id ctx)
        retriggered-step-id (:retriggered-step-id ctx)]
    (cond
      (or
        (step-id/parent-of? cur-step-id retriggered-step-id)
        (= cur-step-id retriggered-step-id)) :rerun
      (step-id/later-than? cur-step-id retriggered-step-id) :run
      :else :mock)))

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

(defn wrap-retrigger-handling [handler retrigger-predicate]
  (fn [step-contexts-and-steps args ctx]
    (handler (add-retrigger-mocks retrigger-predicate ctx step-contexts-and-steps)
             args
             ctx)))

