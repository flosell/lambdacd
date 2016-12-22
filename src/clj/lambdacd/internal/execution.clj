(ns lambdacd.internal.execution
  "low level functions for job-execution"
  (:require [clojure.core.async :as async]
            [lambdacd.state.core :as state]
            [clojure.tools.logging :as log]
            [lambdacd.step-id :as step-id]
            [lambdacd.steps.status :as status]
            [lambdacd.event-bus :as event-bus]
            [lambdacd.steps.result :as step-results]
            [lambdacd.presentation.pipeline-structure :as pipeline-structure]
            [lambdacd.execution.internal.execute-step :as execute-step]
            [lambdacd.execution.internal.util :as execution-util]))

(defn merge-two-step-results [r1 r2]
  (step-results/merge-two-step-results r1 r2 :resolvers [step-results/status-resolver
                                                         step-results/merge-nested-maps-resolver
                                                         step-results/combine-to-list-resolver
                                                         step-results/second-wins-resolver]))

(defn- to-context-and-step [ctx]
  (fn [idx step]
    (let [parent-step-id (:step-id ctx)
          new-step-id    (step-id/child-id parent-step-id (inc idx))
          step-ctx       (assoc ctx :step-id new-step-id)]
      [step-ctx step])))

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

(defn contexts-for-steps
  "creates contexts for steps"
  [steps base-context]
  (map-indexed (to-context-and-step base-context) steps))

(defn keep-globals [old-args step-result]
  (let [existing-globals              (:global old-args)
        new-globals                   (:global step-result)
        merged-globals                (merge existing-globals new-globals)
        args-with-old-and-new-globals (assoc step-result :global merged-globals)]
    args-with-old-and-new-globals))


(defn keep-original-args [old-args step-result]
  (merge old-args step-result))

(defn not-success? [step-result]
  (not= :success (:status step-result)))

(defn serial-step-result-producer [& {:keys [stop-predicate]
                                      :or   {stop-predicate not-success?}}]
  (fn [args s-with-id]
    (loop [result                  ()
           remaining-steps-with-id s-with-id
           cur-args                args]
      (if (empty? remaining-steps-with-id)
        result
        (let [ctx-and-step (first remaining-steps-with-id)
              step-result  (execute-step/execute-step cur-args ctx-and-step)
              step-output  (first (vals (:outputs step-result)))
              new-result   (cons step-result result)
              new-args     (->> step-output
                                (keep-globals cur-args)
                                (keep-original-args args))]
          (if (stop-predicate step-result)
            new-result
            (recur (cons step-result result) (rest remaining-steps-with-id) new-args)))))))

(defn- inherit-message-from-parent? [parent-ctx]
  (fn [msg]
    (let [msg-step-id          (:step-id msg)
          parent-step-id       (:step-id parent-ctx)
          msg-build            (:build-number msg)
          parent-build         (:build-number parent-ctx)
          msg-from-child?      (step-id/direct-parent-of? parent-step-id msg-step-id)
          msg-from-same-build? (= parent-build msg-build)]
      (and msg-from-child? msg-from-same-build?))))


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

(defn sequential-retrigger-predicate [ctx step]
  (let [cur-step-id         (:step-id ctx)
        retriggered-step-id (:retriggered-step-id ctx)]
    (cond
      (or
        (step-id/parent-of? cur-step-id retriggered-step-id)
        (= cur-step-id retriggered-step-id)) :rerun
      (step-id/later-than? cur-step-id retriggered-step-id) :run
      :else :mock)))

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

(def not-nil? (complement nil?))

(defn unify-only-status [unify-status-fn]
  (fn [step-results]
    {:status (unify-status-fn (->> step-results
                                   (vals)
                                   (map :status)))}))

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
        result                             (reduce merge-two-step-results step-results)]
    (event-bus/unsubscribe ctx :step-result-updated subscription)
    result))

(defn run [pipeline context]
  (let [build-number (state/next-build-number context)]
    (state/consume-pipeline-structure context build-number (pipeline-structure/pipeline-display-representation pipeline))
    (let [runnable-pipeline (map eval pipeline)]
      (execute-steps runnable-pipeline {} (merge context {:result-channel (async/chan (async/dropping-buffer 0))
                                                          :step-id        []
                                                          :build-number   build-number})))))

(defn retrigger [pipeline context build-number step-id-to-run next-build-number]
  (let [executable-pipeline (map eval pipeline)]
    (state/consume-pipeline-structure context next-build-number (pipeline-structure/pipeline-display-representation pipeline))
    (execute-steps executable-pipeline {} (assoc context :step-id []
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
