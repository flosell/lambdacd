(ns lambdacd.internal.execution
  "low level functions for job-execution"
  (:require [clojure.core.async :as async]
            [lambdacd.internal.pipeline-state :as pipeline-state]
            [clojure.tools.logging :as log]
            [lambdacd.step-id :as step-id]
            [lambdacd.steps.status :as status]
            [clojure.repl :as repl]
            [lambdacd.event-bus :as event-bus])
  (:import (java.io StringWriter)
           (java.util UUID)))

(defn- step-output [step-id step-result]
  {:outputs { step-id step-result}
   :status (get step-result :status)})

(defn- is-finished [key value]
  (and (= key :status) (not= value :waiting)))

(defn- attach-wait-indicator-if-necessary [result k v]
  (if (and (= k :status) (= v :waiting))
    (assoc result :has-been-waiting true)
    result))

(defn- send-step-result!! [{step-id :step-id build-number :build-number :as ctx} step-result]
  (let [payload {:build-number build-number
                 :step-id      step-id
                 :step-result  step-result}]
    (event-bus/publish!! ctx :step-result-updated payload)))

(defn process-channel-result-async [c {step-id :step-id build-number :build-number :as ctx}]
  (async/go-loop [cur-result {:status :running}]
      (let [[key value] (async/<! c)
            new-result (-> cur-result
                           (assoc key value)
                           (attach-wait-indicator-if-necessary key value))]
        (if (and (nil? key) (nil? value))
          cur-result
          (do
            (event-bus/publish! ctx :step-result-updated {:build-number build-number
                                                           :step-id      step-id
                                                           :step-result  new-result})
            (recur new-result))))))

(defmacro with-err-str
  [& body]
  `(let [s# (new StringWriter)]
     (binding [*err* s#]
       ~@body
       (str s#))))

(defn- execute-or-catch [step args ctx]
  (try
    (let [step-result (step args ctx)]
      (if (nil? (:status step-result))
        {:status :failure :out "step did not return any status!"}
        step-result))
    (catch Throwable e
      {:status :failure :out (with-err-str (repl/pst e))})
    (finally
      (async/close! (:result-channel ctx)))))

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

(defn kill-step-handling [ctx]
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

(defn clean-up-kill-handling [ctx subscription]
  (event-bus/unsubscribe ctx :kill-step subscription))

(defn- report-step-finished [ctx complete-step-result]
  (event-bus/publish ctx :step-finished {:step-id      (:step-id ctx)
                                         :build-number (:build-number ctx)
                                         :final-result complete-step-result
                                         :rerun-for-retrigger (boolean
                                                                (and (:retriggered-build-number ctx)
                                                                     (:retriggered-step-id ctx)))}))

(defn- report-step-started [ctx]
  (send-step-result!! ctx {:status :running})
  (event-bus/publish ctx :step-started  {:step-id      (:step-id ctx)
                                         :build-number (:build-number ctx)}))

(defn report-received-kill [ctx]
  (async/>!! (:result-channel ctx) [:received-kill true]))

(defn add-kill-switch-reporter [ctx]
  (add-watch (:is-killed ctx) (UUID/randomUUID) (fn [_ _ _ new-is-killed-val]
                                                   (if new-is-killed-val
                                                     (report-received-kill ctx)))))

(defn execute-step [args [ctx step]]
  (let [step-id (:step-id ctx)
        result-ch (async/chan)
        child-kill-switch (atom false)
        parent-kill-switch (:is-killed ctx)
        watch-key (UUID/randomUUID)
        _ (add-watch parent-kill-switch watch-key (fn [key reference old new] (reset! child-kill-switch new)))
        _ (reset! child-kill-switch @parent-kill-switch) ; make sure kill switch has the parents state in the beginning and is updated through the watch
        ctx-for-child (assoc ctx :result-channel result-ch
                                 :is-killed child-kill-switch)
        _ (add-kill-switch-reporter ctx-for-child)
        processed-async-result-ch (process-channel-result-async result-ch ctx)
        kill-subscription (kill-step-handling ctx-for-child)
        _ (report-step-started ctx)
        immediate-step-result (execute-or-catch step args ctx-for-child)
        processed-async-result (async/<!! processed-async-result-ch)
        complete-step-result (merge processed-async-result immediate-step-result)]
    (log/debug (str "executed step " step-id complete-step-result))
    (clean-up-kill-handling ctx-for-child kill-subscription)
    (remove-watch parent-kill-switch watch-key)
    (send-step-result!! ctx complete-step-result)
    (report-step-finished ctx complete-step-result)
    (step-output step-id complete-step-result)))

(defn- merge-entry [r1 r2]
  (cond
    (keyword? r1) (status/choose-last-or-not-success r1 r2)
    (and (coll? r1) (coll? r2)) (into r1 r2)
    (coll? r1) (merge r1 r2)
    :else r2))

(defn merge-two-step-results [r1 r2]
  (merge-with merge-entry r1 r2))

(defn- to-context-and-step [ctx]
  (fn [idx step]
    (let [parent-step-id (:step-id ctx)
          new-step-id (step-id/child-id parent-step-id (inc idx))
          step-ctx (assoc ctx :step-id new-step-id)]
      [step-ctx step])))

(defn- process-inheritance [step-results-channel unify-status-fn]
  (let [out-ch (async/chan)]
    (async/go
      (loop [statuses {}]
        (if-let [step-result-update (async/<! step-results-channel)]
          (let [step-status (get-in step-result-update [:step-result :status])
                new-statuses (assoc statuses (:step-id step-result-update) step-status)
                old-unified (unify-status-fn (vals statuses))
                new-unified (unify-status-fn (vals new-statuses))]
            (if (not= old-unified new-unified)
              (async/>! out-ch [:status new-unified]))
            (recur new-statuses))
          (async/close! out-ch))))
    out-ch))

(defn- inherit-from [step-results-channel own-result-channel unify-status-fn]
  (let [status-channel (process-inheritance step-results-channel unify-status-fn)]
    (async/pipe status-channel own-result-channel)))

(defn contexts-for-steps
  "creates contexts for steps"
  [steps base-context]
  (map-indexed (to-context-and-step base-context) steps))

(defn keep-globals [old-args step-result]
  (let [existing-globals (:global old-args)
        new-globals (:global step-result)
        merged-globals (merge existing-globals new-globals)
        args-with-old-and-new-globals (assoc step-result :global merged-globals)]
    args-with-old-and-new-globals))


(defn- keep-original-args [old-args step-result]
  (merge old-args step-result))

(defn- serial-step-result-producer [args s-with-id]
  (loop [result ()
         remaining-steps-with-id s-with-id
         cur-args args]
    (if (empty? remaining-steps-with-id)
      result
      (let [ctx-and-step (first remaining-steps-with-id)
            step-result (execute-step cur-args ctx-and-step)
            step-output (first (vals (:outputs step-result)))
            new-result (cons step-result result)
            new-args (->> step-output
                          (keep-globals cur-args)
                          (keep-original-args args))]
        (if (not= :success (:status step-result))
          new-result
          (recur (cons step-result result) (rest remaining-steps-with-id) new-args))))))

(defn- inherit-message-from-parent? [parent-ctx]
  (fn [msg]
    (let [msg-step-id          (:step-id msg)
          parent-step-id       (:step-id parent-ctx)
          msg-build            (:build-number msg)
          parent-build         (:build-number parent-ctx)
          msg-from-child?      (step-id/direct-parent-of? parent-step-id msg-step-id)
          msg-from-same-build? (= parent-build msg-build)]
      (and msg-from-child? msg-from-same-build?))))


(defn- publish-child-step-results [ctx retriggered-build-number original-build-result]
  (->> original-build-result
       (filter #(step-id/parent-of? (:step-id ctx) (first %)))
       (map #(send-step-result!! (assoc ctx :step-id (first %)) (assoc (second %) :retrigger-mock-for-build-number retriggered-build-number)))
       (doall)))

(defn retrigger-mock-step [retriggered-build-number]
  (fn [args ctx]
    (let [state (pipeline-state/get-all (:pipeline-state-component ctx))
          original-build-result (get state retriggered-build-number)
          original-step-result (get original-build-result (:step-id ctx))]
      (publish-child-step-results ctx retriggered-build-number original-build-result)
      (assoc original-step-result
        :retrigger-mock-for-build-number retriggered-build-number))))

(defn- clear-retrigger-data [ctx]
  (assoc ctx
    :retriggered-build-number nil
    :retriggered-step-id nil))

(defn sequential-retrigger-predicate [ctx step]
  (let [cur-step-id (:step-id ctx)
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

(defn execute-steps [steps args ctx & {:keys [step-result-producer is-killed unify-status-fn retrigger-predicate]
                                       :or   {step-result-producer serial-step-result-producer
                                              is-killed            (atom false)
                                              unify-status-fn      status/successful-when-all-successful
                                              retrigger-predicate  sequential-retrigger-predicate}}]
  (let [steps (filter not-nil? steps)
        base-ctx-with-kill-switch (assoc ctx :is-killed is-killed)
        subscription (event-bus/subscribe ctx :step-result-updated)
        children-step-results-channel (->> subscription
                                           (event-bus/only-payload)
                                           (async/filter< (inherit-message-from-parent? ctx)))
        step-contexts (contexts-for-steps steps base-ctx-with-kill-switch)
        _ (inherit-from children-step-results-channel (:result-channel ctx)  unify-status-fn)
        step-contexts-with-retrigger-mocks (add-retrigger-mocks retrigger-predicate ctx step-contexts)
        step-results (step-result-producer args step-contexts-with-retrigger-mocks)
        result (reduce merge-two-step-results step-results)]
    (event-bus/unsubscribe ctx :step-result-updated subscription)
    result))

(defn run [pipeline context]
  (let [build-number (pipeline-state/next-build-number (:pipeline-state-component context))]
    (let [runnable-pipeline (map eval pipeline)]
      (execute-steps runnable-pipeline {} (merge context {:result-channel (async/chan (async/dropping-buffer 0))
                                                          :step-id []
                                                          :build-number build-number})))))

(defn retrigger [pipeline context build-number step-id-to-run next-build-number]
  (let [executable-pipeline (map eval pipeline) ]
    (execute-steps executable-pipeline {} (assoc context :step-id []
                                                         :result-channel (async/chan (async/dropping-buffer 0))
                                                         :build-number next-build-number
                                                         :retriggered-build-number build-number
                                                         :retriggered-step-id      step-id-to-run))))

(defn retrigger-async [pipeline context build-number step-id-to-run]
  (let [next-build-number (pipeline-state/next-build-number (:pipeline-state-component context))]
    (async/thread
      (retrigger pipeline context build-number step-id-to-run next-build-number ))
    next-build-number))

(defn kill-step [ctx build-number step-id]
  (event-bus/publish ctx :kill-step {:step-id      step-id
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
  (event-bus/publish ctx :kill-step {:step-id      :any-root
                                     :build-number :any})
  (wait-for-pipelines-to-complete ctx))
