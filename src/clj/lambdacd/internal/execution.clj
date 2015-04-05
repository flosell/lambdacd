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

(defn process-channel-result-async [c ctx]
  (async/go
    (loop [cur-result {}]
      (let [[key value] (async/<! c)
            new-result (assoc cur-result key value)]
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

(defn execute-step
  ([args [ctx step]]
    (execute-step step args ctx))
  ([step args {:keys [step-id] :as ctx}]
   (pipeline-state/running ctx)
   (let [result-ch (async/chan 10)
         ctx-with-result-ch (assoc ctx :result-channel result-ch)
         processed-async-result-ch (process-channel-result-async result-ch ctx)
         immediate-step-result (execute-or-catch step args ctx-with-result-ch)
         processed-async-result (async/<!! processed-async-result-ch)
         complete-step-result (merge  processed-async-result immediate-step-result)
         processed-final-result (process-final-result complete-step-result ctx)
         ]
     (log/debug (str "executed step " step-id processed-final-result))
     (step-output step-id processed-final-result))))

(defn- merge-status [s1 s2]
  (if (= s1 :success)
    s2
    (if (= s2 :success)
      s1
      s2)))

(defn- merge-entry [r1 r2]
  (if (keyword? r1)
    (merge-status r1 r2)
    (merge r1 r2)))

(defn- merge-step-results [r1 r2]
  (merge-with merge-entry r1 r2))

(defn- steps-with-ids [steps prev-id]
  (let [significant-part (first prev-id)
        rest-part (rest prev-id)
        significant-ids (util/range-from significant-part (count steps))
        ids (map #(cons %1 rest-part) significant-ids)]
    (map vector ids steps)))

(defn- to-step-with-context [ctx]
  (fn [[id step]]
    [(assoc ctx :step-id id) step]))

(defn contexts-for-steps
  "creates contexts for steps"
  [steps base-context]
  (let [s-with-id (steps-with-ids steps (:step-id base-context))]
    (map (to-step-with-context base-context) s-with-id)))

(defn- keep-globals [step-result old-args]
  (let [existing-globals (:global old-args)
        new-globals (:global step-result)
        merged-globals (merge existing-globals new-globals)
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

(defn execute-steps
  ([steps args ctx]
    (execute-steps serial-step-result-producer steps args ctx))
  ([step-result-producer steps args ctx]
   (execute-steps step-result-producer steps args ctx (atom false)))
   ([step-result-producer steps args ctx is-killed]
    (let [base-ctx-with-kill-switch (assoc ctx :is-killed is-killed)
          step-results (step-result-producer args (contexts-for-steps steps base-ctx-with-kill-switch))]
      (reduce merge-step-results step-results))))

(defn- new-base-id-for [step-id]
  (cons 0 step-id))

(defn new-base-context-for [ctx]
  (let [old-step-id (:step-id ctx)
        new-step-id (new-base-id-for old-step-id)
        new-context (assoc ctx :step-id new-step-id)]
    new-context))

(defn run [pipeline context]
  (let [build-number (pipeline-state/next-build-number context)]
    (let [runnable-pipeline (map eval pipeline)]
      (execute-steps runnable-pipeline {} (merge context {:step-id [0]
                                                          :build-number build-number})))))

(defn add-result [new-build-number retriggered-build-number initial-ctx [step-id result]]
  (let [ctx (assoc initial-ctx :build-number new-build-number :step-id step-id)
        result-with-annotation (assoc result :retrigger-mock-for-build-number retriggered-build-number)]
    (pipeline-state/update ctx result-with-annotation)))

(defn to-be-duplicated? [step-id-retriggered [cur-step-id _]]
  (let [result (step-id/before? cur-step-id step-id-retriggered)]
    result))

(defn duplicate-step-results-not-running-again [new-build-number retriggered-build-number pipeline-history context step-id-to-run]
  (let [do-add-result (partial add-result new-build-number retriggered-build-number context)
        history-to-duplicate (filter (partial to-be-duplicated? step-id-to-run) pipeline-history)]
    (dorun (map do-add-result history-to-duplicate))))

(defn retrigger [pipeline context build-number step-id-to-run]
  (if (> (count step-id-to-run) 1)
    (throw (IllegalArgumentException. "retriggering nested steps is not supported at this point"))
    (let [pipeline-state (deref (:_pipeline-state context))
          pipeline-history (get pipeline-state build-number)
          root-step-number (first step-id-to-run)
          pipeline-fragment-to-run (nthrest pipeline (dec root-step-number))
          executable-pipeline (map eval pipeline-fragment-to-run)
          new-build-number (pipeline-state/next-build-number context)]
      (duplicate-step-results-not-running-again new-build-number build-number pipeline-history context step-id-to-run)
      (execute-steps executable-pipeline {} (assoc context :step-id [(dec root-step-number)]
                                                           :build-number new-build-number)))))

(defn killed? [ctx]
  @(:is-killed ctx))

(defmacro if-not-killed [ctx & body]
  `(if (killed? ~ctx)
     (do
       (async/>!! (:result-channel ~ctx) [:status :killed])
       {:status :killed})
     ~@body))
