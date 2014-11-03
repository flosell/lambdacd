(ns lambdacd.execution
  "low level functions for job-execution"
  (:require [clojure.core.async :as async]
            [lambdacd.util :as util]
            [lambdacd.pipeline-state :as pipeline-state]
            [clojure.tools.logging :as log]))

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

(defn- process-channel-result [c ctx]
  (async/<!!
    (async/go
      (loop [cur-result {:status :running}]
        (let [[key value] (async/<! c)
              new-result (assoc cur-result key value)]
          (pipeline-state/update ctx new-result)
          (if (is-finished key value)
            new-result
            (recur new-result)))))))

(defn- process-static-result [step-result ctx]
  (pipeline-state/update ctx step-result)
  step-result)

(defn- process-step-result [immediate-step-result ctx]
  (if (util/is-channel? immediate-step-result)
    (process-channel-result immediate-step-result ctx)
    (process-static-result immediate-step-result ctx)))

(defn execute-step
  ([args [ctx step]]
    (execute-step step args ctx))
  ([step args {:keys [step-id] :as ctx}]
   (pipeline-state/running ctx)
   (let [immediate-step-result (step args ctx)
         final-step-result (process-step-result immediate-step-result ctx)]
     (log/debug (str "executed step " step-id final-step-result))
     (step-output step-id final-step-result))))

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

(defn- map-or-abort [f coll]
  (loop [result ()
         r coll]
    (if (empty? r)
      result
      (let [map-result (f (first r))
            new-result (cons map-result result)]
        (if (not= :success (:status map-result))
          new-result
          (recur (cons map-result result) (rest r)))))))

(defn- serial-step-result-producer [args s-with-id]
  (map-or-abort (partial execute-step args) s-with-id))

(defn execute-steps
  ([steps args ctx]
    (execute-steps serial-step-result-producer steps args ctx))
  ([step-result-producer steps args ctx]
    (let [step-results (step-result-producer args (context-for-steps steps ctx))]
      (reduce merge-step-results args step-results))))

(defn- new-base-id-for [step-id]
  (cons 0 step-id))

(defn new-base-context-for [ctx]
  (let [old-step-id (:step-id ctx)
        new-step-id (new-base-id-for old-step-id)
        new-context (assoc ctx :step-id new-step-id)]
    new-context))


(def current-build-number (atom 0))

(defn run [pipeline context]
  (let [build-number (swap! current-build-number inc)]
    (let [runnable-pipeline (map eval pipeline)]
      (execute-steps runnable-pipeline {} (merge context {:step-id [0]
                                                          :build-number build-number})))))