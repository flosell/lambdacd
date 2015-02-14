(ns lambdacd.execution
  "low level functions for job-execution"
  (:require [clojure.core.async :as async]
            [lambdacd.util :as util]
            [lambdacd.pipeline-state :as pipeline-state]
            [clojure.tools.logging :as log]
            [clojure.repl :as repl])
  (:import (java.io StringWriter)))

(defn wait-for [p]
  (while (not (p))
    (Thread/sleep 1000))
  {:status :success})


(defn- step-output [step-id step-result]
  {:outputs { step-id step-result}
   :status (get step-result :status :undefined)
  })

(defn- is-finished [key value]
  (and (= key :status) (not= value :waiting)))

(defn process-channel-result-async [c ctx]
  (async/go
    (loop [cur-result {:status :running}]
      (let [[key value] (async/<! c)
            new-result (assoc cur-result key value)]
        (if (and (nil? key) (nil? value))
          cur-result
          (do
            (pipeline-state/update ctx new-result)
             (if (is-finished key value)
               new-result
               (recur new-result))))))))

(defn- process-channel-result [c ctx]
  (async/<!!
    (process-channel-result-async c ctx)))

(defn- process-static-result [step-result ctx]
  (let [new-step-result (assoc step-result :status (get step-result :status :undefined))]
    (pipeline-state/update ctx new-step-result)
    new-step-result))

(defn- process-step-result [immediate-step-result ctx]
  (if (util/is-channel? immediate-step-result)
    (process-channel-result immediate-step-result ctx)
    (process-static-result immediate-step-result ctx)))

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
         processed-result-ch (process-channel-result-async result-ch ctx)
         immediate-step-result (execute-or-catch step args ctx-with-result-ch)
         final-step-result (process-step-result immediate-step-result ctx)
         processed-result (async/<!! processed-result-ch)
         final-step-result-with-processed-result-ch (merge  processed-result final-step-result)]
     (log/debug (str "executed step " step-id final-step-result-with-processed-result-ch))
     (step-output step-id final-step-result-with-processed-result-ch))))

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

(defn context-for-steps
  "creates contexts for steps"
  [steps base-context]
  (let [s-with-id (steps-with-ids steps (:step-id base-context))]
    (map (to-step-with-context base-context) s-with-id)))

(defn- keep-globals [old-args step-result]
  (let [existing-globals (:global old-args)
        new-globals (:global step-result)
        merged-globals (merge existing-globals new-globals)
        args-with-old-and-new-globals (assoc step-result :global merged-globals)]
    args-with-old-and-new-globals))

(defn- map-or-abort [args coll]
  (loop [result ()
         r coll
         cur-args args]
    (if (empty? r)
      result
      (let [step (first r)
            map-result (execute-step cur-args step)
            new-result (cons map-result result)
            step-output (first (vals (:outputs map-result)))
            new-args (keep-globals cur-args step-output)]
        (if (not= :success (:status map-result))
          new-result
          (recur (cons map-result result) (rest r) new-args))))))

(defn- serial-step-result-producer [args s-with-id]
  (map-or-abort args s-with-id))

(defn execute-steps
  ([steps args ctx]
    (execute-steps serial-step-result-producer steps args ctx))
  ([step-result-producer steps args ctx]
    (let [step-results (step-result-producer args (context-for-steps steps ctx))]
      ;(log/debug "The step-results: {}" step-results)
      (reduce merge-step-results args step-results))))

(defn- new-base-id-for [step-id]
  (cons 0 step-id))

(defn new-base-context-for [ctx]
  (let [old-step-id (:step-id ctx)
        new-step-id (new-base-id-for old-step-id)
        new-context (assoc ctx :step-id new-step-id)]
    new-context))



(defn run [pipeline context]
  (let [build-number (+ 1 (pipeline-state/current-build-number context))]
    (let [runnable-pipeline (map eval pipeline)]
      (execute-steps runnable-pipeline {} (merge context {:step-id [0]
                                                          :build-number build-number})))))

(defn mock-for [step-id pipline-history]
  (fn [& _]
    (get pipline-history step-id)))

(defn- mock-pipeline-until-step [pipeline build-number context [first-part-of-step-id]]
  (let [pipeline-state (deref (:_pipeline-state context))
        pipeline-history (get pipeline-state build-number)
        indexed-pipeline (map-indexed (fn [idx step] [(inc idx) step]) pipeline)
        indexed-pipeline-with-mocks (util/map-if
                              (fn [[step-part step]] (< step-part first-part-of-step-id))
                              (fn [[step-part step]] [step-part (mock-for [step-part] pipeline-history)])
                              indexed-pipeline)
        pipeline-with-mocks (map (fn [[step-part step]] step) indexed-pipeline-with-mocks)]
    pipeline-with-mocks))


(defn retrigger [pipeline context build-number step-id-to-run]
  (let [pipeline-with-mocks (mock-pipeline-until-step pipeline build-number context step-id-to-run)
        executable-pipeline (map eval pipeline-with-mocks)]
    (execute-steps executable-pipeline {} (assoc context :step-id [0]
                                                         :build-number build-number))))