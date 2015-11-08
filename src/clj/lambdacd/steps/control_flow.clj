(ns lambdacd.steps.control-flow
  "control flow elements for a pipeline: steps that control the way their child-steps are being run"
  (:require [lambdacd.core :as core]
            [clojure.core.async :as async]
            [lambdacd.steps.support :as support]
            [lambdacd.internal.step-id :as step-id]
            [lambdacd.steps.status :as status])
  (:import (java.util UUID)))

(defn- post-process-container-results [result]
  (let [outputs (vals (:outputs result))
        globals (support/merge-globals outputs)
        merged-step-results (support/merge-step-results outputs)]
    (merge merged-step-results result {:global globals})))

(defn- is-finished [{status :status}]
  (or
    (= :success status)
    (= :killed status)))

(defn- wait-for-finished-on [channels]
  (let [merged (async/merge channels)
        filtered-by-success (async/filter< is-finished merged)]
    (async/<!! filtered-by-success)))

(defn- step-producer-returning-with-first-successful [args steps-and-ids]
  (let [step-result-channels (map #(async/go (core/execute-step args %)) steps-and-ids)
        result (wait-for-finished-on step-result-channels)]
    (if (nil? result)
      [{:status :failure}]
      [result])))

(defn synchronize-atoms [source target]
  (let [key (UUID/randomUUID)]
    (add-watch source key #(reset! target %4))
    key))

(defn ^{:display-type :parallel} either [& steps]
  (fn [args ctx]
    (let [parent-kill-switch (:is-killed ctx)
          kill-switch (atom false)
          watch-ref (synchronize-atoms parent-kill-switch kill-switch)
          _ (reset! kill-switch @parent-kill-switch)
          execute-output (core/execute-steps steps args ctx
                                             :is-killed kill-switch
                                             :step-result-producer step-producer-returning-with-first-successful
                                             :retrigger-predicate (constantly true)
                                             :unify-status-fn status/successful-when-one-successful)]
      (reset! kill-switch true)
      (remove-watch parent-kill-switch watch-ref)
      (if (= :success (:status execute-output))
        (first (vals (:outputs execute-output)))
        execute-output))))

(defn- parallel-retrigger-predicate [ctx step]
  (let [cur-step-id (:step-id ctx)
        retriggered-step-id (:retriggered-step-id ctx)]
    (or
      (step-id/parent-of? cur-step-id retriggered-step-id)
      (step-id/parent-of? retriggered-step-id cur-step-id)
      (= cur-step-id retriggered-step-id))))

(defn- parallel-step-result-producer [args steps-and-ids]
  (pmap #(core/execute-step args %) steps-and-ids))

(defn- execute-steps-in-parallel [steps args ctx]
  (core/execute-steps steps args ctx
                      :step-result-producer parallel-step-result-producer
                      :unify-status-fn status/successful-when-all-successful
                      :retrigger-predicate parallel-retrigger-predicate
                      :is-killed (:is-killed ctx)))

(defn ^{:display-type :parallel} in-parallel [& steps]
  (fn [args ctx]
    (post-process-container-results
      (execute-steps-in-parallel steps args ctx))))


(defn ^{:display-type :container} in-cwd [cwd & steps]
  (fn [args ctx]
    (post-process-container-results
      (core/execute-steps steps (assoc args :cwd cwd) ctx
                          :unify-status-fn status/successful-when-all-successful))))

(defn ^{:display-type :container} run [ & steps]
  (fn [args ctx]
    (post-process-container-results
      (core/execute-steps steps args ctx
                          :unify-status-fn status/successful-when-all-successful
                          :is-killed (:is-killed ctx)))))

(defn- child-context [parent-ctx child-number]
  (let [parent-step-id (:step-id parent-ctx)
        child-step-id  (step-id/child-id parent-step-id child-number)]
    (assoc parent-ctx :step-id child-step-id)))

(defn ^{:display-type :parallel} junction [condition-step success-step failiure-step]
  (fn [args ctx]
    (post-process-container-results
      (let [condition-step-result (core/execute-step args (child-context ctx 1) condition-step)]
        (if (= :success (:status condition-step-result))
          (core/execute-step args (child-context ctx 2) success-step)
          (core/execute-step args (child-context ctx 3) failiure-step))))))
