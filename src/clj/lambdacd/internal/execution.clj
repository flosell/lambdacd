(ns lambdacd.internal.execution
  "low level functions for job-execution"
  (:require [clojure.core.async :as async]
            [lambdacd.internal.pipeline-state :as pipeline-state]
            [clojure.tools.logging :as log]
            [lambdacd.internal.step-id :as step-id]
            [lambdacd.internal.step-results :as step-results]
            [lambdacd.steps.status :as status]
            [clojure.repl :as repl])
  (:import (java.io StringWriter)))

(defn- step-output [step-id step-result]
  {:outputs { step-id step-result}
   :status (get step-result :status)})

(defn- is-finished [key value]
  (and (= key :status) (not= value :waiting)))

(defn- attach-wait-indicator-if-necessary [result k v]
  (if (and (= k :status) (= v :waiting))
    (assoc result :has-been-waiting true)
    result))

(defn process-channel-result-async [c ctx]
  (async/go
    (loop [cur-result {:status :running}]
      (let [[key value] (async/<! c)
            new-result (-> cur-result
                           (assoc key value)
                           (attach-wait-indicator-if-necessary key value))]
        (if (and (nil? key) (nil? value))
          cur-result
          (do
            (step-results/send-step-result ctx new-result)
            (pipeline-state/update ctx new-result)
            (recur new-result)))))))

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

(defn- reuse-from-history-if-required [{:keys [step-id] :as ctx} {build-number-to-resuse :reuse-from-build-number :as result}]
  (if build-number-to-resuse
    (let [state @(:_pipeline-state ctx)
          old-result (get-in state [build-number-to-resuse step-id])]
      (assoc old-result :retrigger-mock-for-build-number build-number-to-resuse))
    result))

(defn execute-step [args [ctx step]]
 (pipeline-state/running ctx)
 (let [_ (step-results/send-step-result ctx {:status :running})
       step-id (:step-id ctx)
       result-ch (async/chan 10)
       ctx-with-result-ch (assoc ctx :result-channel result-ch)
       processed-async-result-ch (process-channel-result-async result-ch ctx)
       immediate-step-result (execute-or-catch step args ctx-with-result-ch)
       step-result-or-history (reuse-from-history-if-required ctx immediate-step-result)
       processed-async-result (async/<!! processed-async-result-ch)
       complete-step-result (merge processed-async-result step-result-or-history)]
   (log/debug (str "executed step " step-id complete-step-result))
   (step-results/send-step-result ctx complete-step-result)
   (pipeline-state/update ctx complete-step-result)
   (step-output step-id complete-step-result)))

(defn- merge-status [s1 s2]
  (if (= s1 :success)
    s2
    (if (= s2 :success)
      s1
      s2)))

(defn- merge-entry [r1 r2]
  (cond
    (keyword? r1) (merge-status r1 r2)
    (coll? r1) (merge r1 r2)
    :else r2))

(defn merge-two-step-results [r1 r2]
  (merge-with merge-entry r1 r2))

(defn- to-context-and-step [ctx step-results-channel]
  (fn [idx step]
    (let [parent-step-id (:step-id ctx)
          new-step-id (cons (inc idx) parent-step-id)
          step-ctx (assoc ctx :step-id new-step-id
                              :step-results-channel step-results-channel)]
    [step-ctx step])))

(defn- process-inheritance [step-results-channel own-step-results-channel unify-status-fn]
  (let [out-ch (async/chan 100)]
    (async/go
      (loop [statuses {}]
        (if-let [step-result-update (async/<! step-results-channel)]
          (do
            (async/>! own-step-results-channel step-result-update)
            (let [step-status (get-in step-result-update [:step-result :status])
                  new-statuses (assoc statuses (:step-id step-result-update) step-status)
                  old-unified (unify-status-fn (vals statuses))
                  new-unified (unify-status-fn (vals new-statuses))]
              (if (not= old-unified new-unified)
                (async/>!! out-ch [:status new-unified]))
              (recur new-statuses)))
          (async/close! out-ch))))
    out-ch))

(defn- inherit-from [step-results-channel own-result-channel own-step-results-channel unify-status-fn]
  (let [status-channel (process-inheritance step-results-channel own-step-results-channel unify-status-fn)]
    (async/pipe status-channel own-result-channel)))

(defn contexts-for-steps
  "creates contexts for steps"
  [steps base-context step-results-channel]
  (map-indexed (to-context-and-step base-context step-results-channel) steps))

(defn keep-globals [step-result old-args]
  (let [existing-globals (:global old-args)
        new-globals (:global step-result)
        merged-globals (merge new-globals existing-globals)
        args-with-old-and-new-globals (assoc step-result :global merged-globals)]
    args-with-old-and-new-globals))


(defn- keep-original-args [step-result old-args]
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
            new-args (-> step-output
                       (keep-globals cur-args)
                       (keep-original-args args))]
        (if (not= :success (:status step-result))
          new-result
          (recur (cons step-result result) (rest remaining-steps-with-id) new-args))))))

(defn execute-steps [steps args ctx & {:keys [step-result-producer is-killed unify-status-fn]
                                       :or   {step-result-producer serial-step-result-producer
                                              is-killed            (atom false)
                                              unify-status-fn      status/successful-when-all-successful}}]
  (let [base-ctx-with-kill-switch (assoc ctx :is-killed is-killed)
        children-step-results-channel (async/chan 100)
        step-contexts (contexts-for-steps steps base-ctx-with-kill-switch children-step-results-channel)
        _ (inherit-from children-step-results-channel (:result-channel ctx) (:step-results-channel ctx) unify-status-fn)
        step-results (step-result-producer args step-contexts)]
    (reduce merge-two-step-results step-results)))

(defn run [pipeline context]
  (let [build-number (pipeline-state/next-build-number context)]
    (let [runnable-pipeline (map eval pipeline)]
      (execute-steps runnable-pipeline {} (merge context {:result-channel (async/chan (async/dropping-buffer 0))
                                                          :step-id []
                                                          :build-number build-number})))))

(defn- add-result [new-build-number retriggered-build-number initial-ctx [step-id result]]
  (let [ctx (assoc initial-ctx :build-number new-build-number :step-id step-id)
        result-with-annotation (assoc result :retrigger-mock-for-build-number retriggered-build-number)]
    (pipeline-state/update ctx result-with-annotation)))

(defn- to-be-duplicated? [step-id-retriggered [cur-step-id _]]
  (let [result (step-id/before? cur-step-id step-id-retriggered)]
    result))

(defn- duplicate-step-results-not-running-again [new-build-number retriggered-build-number pipeline-history context step-id-to-run]
  (let [do-add-result (partial add-result new-build-number retriggered-build-number context)
        history-to-duplicate (filter (partial to-be-duplicated? step-id-to-run) pipeline-history)]
    (dorun (map do-add-result history-to-duplicate))))

(defn mock-step [build-number]
  (fn [& _]
    {:reuse-from-build-number build-number :status :to-be-overwritten}))


(defn- with-step-id [parent-step-id]
  (fn [idx step]
    [(cons (inc idx) parent-step-id) step]))

(declare mock-for-steps)
(defn- replace-with-mock-or-recur [step-id-to-retrigger build-number]
  (fn [[step-id step]]
    (cond
      (and
        (step-id/before? step-id step-id-to-retrigger)
        (not (step-id/parent-of? step-id step-id-to-retrigger))) (cons mock-step [build-number])
      (step-id/parent-of? step-id step-id-to-retrigger) (cons (first step) (mock-for-steps (rest step) step-id step-id-to-retrigger build-number))
      :else step)))

(defn- mock-for-steps [steps cur-step-id step-id-to-retrigger build-number]
  (->> steps
      (map-indexed (with-step-id cur-step-id))
      (map (replace-with-mock-or-recur step-id-to-retrigger build-number))))

(defn retrigger [pipeline context build-number step-id-to-run next-build-number]
  (let [pipeline-state (deref (:_pipeline-state context))
        pipeline-history (get pipeline-state build-number)
        pipeline-fragment-to-run (mock-for-steps pipeline [] step-id-to-run build-number)
        executable-pipeline (map eval pipeline-fragment-to-run) ]
    (duplicate-step-results-not-running-again next-build-number build-number pipeline-history context step-id-to-run)
    (execute-steps executable-pipeline {} (assoc context :step-id []
                                                         :result-channel (async/chan (async/dropping-buffer 0))
                                                         :build-number next-build-number))))

(defn retrigger-async [pipeline context build-number step-id-to-run]
  (let [next-build-number (pipeline-state/next-build-number context)]
    (async/thread
      (retrigger pipeline context build-number step-id-to-run next-build-number ))
    next-build-number))