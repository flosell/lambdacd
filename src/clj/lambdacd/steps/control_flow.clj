(ns lambdacd.steps.control-flow
  "control flow elements for a pipeline: steps that control the way their child-steps are being run"
  (:require [lambdacd.execution.core :as execution]
            [clojure.core.async :as async]
            [lambdacd.steps.support :as support]
            [lambdacd.step-id :as step-id]
            [lambdacd.steps.status :as status]
            [lambdacd.util.internal.temp :as temp-util])
  (:refer-clojure :exclude [alias])
  (:import (java.util UUID)))

(defn- post-process-container-results [result]
  (let [outputs (vals (:outputs result))
        globals (support/merge-globals outputs)
        merged-step-results (support/merge-step-results outputs)]
    (merge merged-step-results result {:global globals})))

(defn- wait-for-finished-on [step-result-chs]
  (let [all-resp-results-ch    (async/merge step-result-chs)
        unified-status         (atom :unknown)
        successful-step-result (async/<!!
                                 (async/go-loop [statuses []]
                                   (if-let [result (async/<! all-resp-results-ch)]
                                     (let [new-statuses (conj statuses (:status result))]
                                       (reset! unified-status (status/successful-when-one-successful new-statuses))
                                       (if (= :success (:status result))
                                         result
                                         (recur new-statuses))))))]
    (if (nil? successful-step-result)
      [{:status @unified-status}]
      [successful-step-result])))

(defn- either-step-result-producer [args steps-and-ids]
  (let [step-result-chs (map #(async/thread (execution/execute-step args %)) steps-and-ids)]
    (wait-for-finished-on step-result-chs)))

(defn synchronize-atoms [source target]
  (let [key (UUID/randomUUID)]
    (add-watch source key #(reset! target %4))
    key))

(defn ^{:display-type :parallel} either [& steps]
  (fn [args ctx]
    (let [parent-kill-switch (:is-killed ctx)
          kill-switch        (atom false)
          watch-ref          (synchronize-atoms parent-kill-switch kill-switch)
          _                  (reset! kill-switch @parent-kill-switch)
          execute-output     (execution/execute-steps steps args ctx
                                                      :is-killed kill-switch
                                                      :step-result-producer either-step-result-producer
                                                      :retrigger-predicate (constantly :rerun)
                                                      :unify-results-fn (support/unify-only-status status/successful-when-one-successful))]
      (reset! kill-switch true)
      (remove-watch parent-kill-switch watch-ref)
      (if (= :success (:status execute-output))
        (first (vals (:outputs execute-output)))
        execute-output))))

(defn- parallel-retrigger-predicate [ctx step]
  (let [cur-step-id (:step-id ctx)
        retriggered-step-id (:retriggered-step-id ctx)]
    (if (or
          (step-id/parent-of? cur-step-id retriggered-step-id)
          (step-id/parent-of? retriggered-step-id cur-step-id)
          (= cur-step-id retriggered-step-id))
      :rerun
      :mock)))

(defn- parallel-step-result-producer [args steps-and-ids]
  (pmap #(execution/execute-step args %) steps-and-ids))

(defn- execute-steps-in-parallel [steps args ctx]
  (execution/execute-steps steps args ctx
                           :step-result-producer parallel-step-result-producer
                           :unify-results-fn (support/unify-only-status status/successful-when-all-successful)
                           :retrigger-predicate parallel-retrigger-predicate
                           :is-killed (:is-killed ctx)))

(defn ^{:display-type :parallel} in-parallel [& steps]
  (fn [args ctx]
    (post-process-container-results
      (execute-steps-in-parallel steps args ctx))))


(defn ^{:display-type :container} in-cwd [cwd & steps]
  (fn [args ctx]
    (post-process-container-results
      (execution/execute-steps steps (assoc args :cwd cwd) ctx
                               :unify-results-fn (support/unify-only-status status/successful-when-all-successful)))))

(defn- run-steps-in-sequence [args ctx steps]
  (post-process-container-results
    (execution/execute-steps steps args ctx
                             :unify-results-fn (support/unify-only-status status/successful-when-all-successful)
                             :is-killed (:is-killed ctx))))


(defn ^{:display-type :container} run [ & steps]
  (fn [args ctx]
    (run-steps-in-sequence args ctx steps)))

(defn- child-context [parent-ctx child-number]
  (let [parent-step-id (:step-id parent-ctx)
        child-step-id  (step-id/child-id parent-step-id child-number)]
    (assoc parent-ctx :step-id child-step-id)))

(defn ^{:display-type :parallel} junction [condition-step success-step failure-step]
  (fn [args ctx]
    (post-process-container-results
      (let [condition-step-result (execution/execute-step args (child-context ctx 1) condition-step)]
        (if (= :success (:status condition-step-result))
          (execution/execute-step args (child-context ctx 2) success-step)
          (execution/execute-step args (child-context ctx 3) failure-step))))))

(defn ^{:is-alias true} alias
  "just runs child but child is displayed with the given alias in visualization"
  [alias child]
  (run child))

(defn with-workspace
  "runs given steps with a clean workspace given to child step as :cwd argument"
  [& steps]
  (fn [args ctx]
    (let [home-dir (:home-dir (:config ctx))
          temp-dir (temp-util/create-temp-dir home-dir)
          new-args  (assoc args :cwd temp-dir)]
      (temp-util/with-temp temp-dir
                       (run-steps-in-sequence new-args ctx steps)))))
