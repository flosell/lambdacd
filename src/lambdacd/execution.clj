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
  ([args [step-id step]]
    (execute-step step args step-id))
  ([step args step-id]
    (pipeline-state/running step-id)
    (let [immediate-step-result (step args step-id)]
      (pipeline-state/update step-id immediate-step-result)
      (let [final-step-result (step-result-after-step-finished immediate-step-result)]
        (log/debug (str "executed step " step-id final-step-result))
        (pipeline-state/update step-id final-step-result)
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

(defn steps-with-ids [steps prev-id]
  (let [significant-part (first prev-id)
        rest-part (rest prev-id)
        significant-ids (util/range-from significant-part (count steps))
        ids (map #(cons %1 rest-part) significant-ids)]
    (map vector ids steps)))

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
  ([steps args step-id]
    (execute-steps serial-step-result-producer steps args step-id))
  ([step-result-producer steps args step-id]
    (let [step-results (step-result-producer args (steps-with-ids steps step-id))]
      (reduce merge-step-results args step-results))))

(defn new-base-id-for [step-id]
  (cons 0 step-id))

(defn run [pipeline]
  (pipeline-state/reset-pipeline-state)
  (let [runnable-pipeline (map eval pipeline)]
    (execute-steps runnable-pipeline {} [0])))
