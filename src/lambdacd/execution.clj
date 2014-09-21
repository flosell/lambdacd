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

(defn- wait-for-success [c]
  (wait-for #(= (async/<!! c) :success)))

(defn- step-result-after-step-finished [step-result]
  (if (util/is-channel? (:status step-result))
    (do
      (wait-for-success (:status step-result))
      (assoc step-result :status :success))
    step-result))


(defn execute-step
  ([args [ctx step]]
    (execute-step step args ctx))
  ([step args {:keys [step-id] :as ctx}]
   (pipeline-state/running ctx)
   (let [immediate-step-result (step args ctx)]
     (pipeline-state/update ctx immediate-step-result)
     (let [final-step-result (step-result-after-step-finished immediate-step-result)]
       (log/debug (str "executed step " step-id final-step-result))
       (pipeline-state/update ctx final-step-result)
       (step-output step-id final-step-result)))))

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

(defn run [pipeline]
  (let [build-number (swap! current-build-number inc)]
    (pipeline-state/reset-pipeline-state)
    (let [runnable-pipeline (map eval pipeline)]
      (execute-steps runnable-pipeline {} {:step-id [0]
                                           :build-number build-number
                                           :_pipeline-state pipeline-state/pipeline-state}))))
