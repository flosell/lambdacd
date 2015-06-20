(ns lambdacd.internal.execution
  "low level functions for job-execution"
  (:require [clojure.core.async :as async]
            [lambdacd.util :as util]
            [lambdacd.internal.pipeline-state :as pipeline-state]
            [clojure.tools.logging :as log]
            [lambdacd.internal.step-id :as step-id]
            [clojure.repl :as repl])
  (:import (java.io StringWriter)))

(defn- step-output [step-id step-result]
  {:outputs { step-id step-result}
   :status (get step-result :status :undefined)})

(defn- is-finished [key value]
  (and (= key :status) (not= value :waiting)))

(defn- attach-wait-indicator-if-necessary [result k v]
  (if (and (= k :status) (= v :waiting))
    (assoc result :has-been-waiting true)
    result))

(defn process-channel-result-async [c ctx]
  (async/go
    (loop [cur-result {}]
      (let [[key value] (async/<! c)
            new-result (-> cur-result
                           (assoc key value)
                           (attach-wait-indicator-if-necessary key value))]
        (if (and (nil? key) (nil? value))
          cur-result
          (do
            (pipeline-state/update ctx new-result)
             (if (is-finished key value)
               new-result
               (recur new-result))))))))

(defn- process-final-result [step-result ctx]
  (let [new-step-result (assoc step-result :status (get step-result :status :undefined))]
    (pipeline-state/update ctx new-step-result)
    new-step-result))

(defmacro with-err-str
  [& body]
  `(let [s# (new StringWriter)]
     (binding [*err* s#]
       ~@body
       (str s#))))

(defn- execute-or-catch [step args ctx]
  (try
    (step args ctx)
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

(defn- copy-to [write-channel & channels-receiving-copies]
  (let [mult (async/mult write-channel)]
    (doall (map #(async/tap mult %) channels-receiving-copies))))

; TODO: the result-channel argument is deprecated, remove after next release
(defn execute-step [args [ctx step] & {:keys [result-channel]}]
 (pipeline-state/running ctx)
 (let [step-id (:step-id ctx)
       result-channel-from-ctx (:result-channel ctx)
       output-result-channel (or result-channel result-channel-from-ctx (async/chan (async/dropping-buffer 0)) )
       result-ch-to-write (async/chan 10)
       result-ch-to-read (async/chan 10)
       _ (copy-to result-ch-to-write output-result-channel result-ch-to-read)
       ctx-with-result-ch (assoc ctx :result-channel result-ch-to-write)
       processed-async-result-ch (process-channel-result-async result-ch-to-read ctx)
       immediate-step-result (execute-or-catch step args ctx-with-result-ch)
       step-result-or-history (reuse-from-history-if-required ctx immediate-step-result)
       processed-async-result (async/<!! processed-async-result-ch)
       complete-step-result (merge processed-async-result step-result-or-history)
       processed-final-result (process-final-result complete-step-result ctx)]
   (log/debug (str "executed step " step-id processed-final-result))
   (step-output step-id processed-final-result)))

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

(defn- to-context-and-step [ctx]
  (fn [idx step]
    (let [parent-step-id (:step-id ctx)
          new-step-id (cons (inc idx) parent-step-id)
          step-ctx (assoc ctx :step-id new-step-id
                              :result-channel (async/chan 10))]
    [step-ctx step])))

(defn- chan-with-idx [idx c]
  (let [out-ch (async/chan 10)]
    (async/go-loop []
      (if-let [v (async/<! c)]
        (do
          (async/>! out-ch [idx v])
          (recur))
        (async/close! out-ch)))
    out-ch))


(defn- unify-statuses [statuses]
  (let [has-failed (util/contains-value? :failure statuses)
        has-running (util/contains-value? :running statuses)
        all-waiting (every? #(= % :waiting) statuses)
        one-ok (util/contains-value? :success statuses)]
    (cond
      has-failed :failure
      one-ok :success
      has-running :running
      all-waiting :waiting
      :else :unknown)))

(defn- send-new-status [statuses out-ch]
  (let [statuses (vals statuses)
        unified (unify-statuses statuses)]
    (async/>!! out-ch [:status unified])))

(defn- process-inheritance [status-with-indexes]
  (let [out-ch (async/chan 100)]
    (async/go
      (loop [statuses {}]
        (if-let [[idx [k v]] (async/<! status-with-indexes)]
          (if (= :status k)
            (let [new-statuses (assoc statuses idx v)]
              (send-new-status new-statuses out-ch)
              (recur new-statuses))
            (recur statuses))
          (async/close! out-ch))))
    out-ch))

(defn- inherit-from [result-channels own-result-channel]
  {:pre [(not (nil? own-result-channel))]}
  (let [channels-with-idx (map-indexed #(chan-with-idx %1 %2) result-channels)
        all-channels (async/merge channels-with-idx)
        status-channel (process-inheritance all-channels)]
    (async/pipe status-channel own-result-channel)))

(defn contexts-for-steps
  "creates contexts for steps"
  [steps base-context]
  (map-indexed (to-context-and-step base-context) steps))

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
      (let [step (first remaining-steps-with-id)
            step-result (execute-step cur-args step)
            step-output (first (vals (:outputs step-result)))
            new-result (cons step-result result)
            new-args (-> step-output
                       (keep-globals cur-args)
                       (keep-original-args args))]
        (if (not= :success (:status step-result))
          new-result
          (recur (cons step-result result) (rest remaining-steps-with-id) new-args))))))

(defn execute-steps [steps args ctx & {:keys [step-result-producer is-killed]
                                       :or   {step-result-producer serial-step-result-producer
                                               is-killed            (atom false)}}]
  (let [base-ctx-with-kill-switch (assoc ctx :is-killed is-killed)
        step-contexts (contexts-for-steps steps base-ctx-with-kill-switch)
        result-channels (map #(:result-channel (first %)) step-contexts)
        _ (inherit-from result-channels (:result-channel ctx))
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
    {:reuse-from-build-number build-number}))


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