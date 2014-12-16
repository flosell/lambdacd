(ns lambdacd.control-flow
  "control flow elements for a pipeline: steps that control the way their child-steps are being run"
  (:require [lambdacd.execution :as execution]
            [clojure.core.async :as async]))

(defn- parallel-step-result-producer [args steps-and-ids]
  (pmap (partial execution/execute-step args) steps-and-ids))

(defn- execute-steps-in-parallel [steps args step-id]
  (execution/execute-steps parallel-step-result-producer steps args step-id))

(defn ^{:display-type :parallel} in-parallel [& steps]
  (fn [args ctx]
    (execute-steps-in-parallel steps args (execution/new-base-context-for ctx))))


(defn- wait-for-success-on [channels]
  (let [merged (async/merge channels)
        filtered-by-success (async/filter< #(= :success (:status %)) merged)]
    (async/<!! filtered-by-success)))

(defn- step-producer-returning-with-first-successful [args steps-and-ids]
  (let [step-result-channels (map #(async/go (execution/execute-step args %)) steps-and-ids)
        result (wait-for-success-on step-result-channels)]
    (if (nil? result)
      [{:status :failure}]
      [result]))
  )

(defn ^{:display-type :parallel} either [& steps]
  (fn [args ctx]
    (execution/execute-steps step-producer-returning-with-first-successful steps args (execution/new-base-context-for ctx))))



(defn ^{:display-type :container} in-cwd [cwd & steps]
  (fn [args ctx]
    (execution/execute-steps steps (assoc args :cwd cwd) (execution/new-base-context-for ctx))))
