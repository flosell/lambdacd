(ns lambdacd.steps.control-flow
  "control flow elements for a pipeline: steps that control the way their child-steps are being run"
  (:require [lambdacd.core :as core]
            [clojure.core.async :as async]
            [lambdacd.steps.support :as support]))

(defn- post-process-container-results [result]
  (let [outputs (vals (:outputs result))
        globals (support/merge-globals outputs)
        merged-step-results (support/merge-step-results outputs)]
    (merge merged-step-results result {:global globals})))

(defn- wait-for-success-on [channels]
  (let [merged (async/merge channels)
        filtered-by-success (async/filter< #(= :success (:status %)) merged)]
    (async/<!! filtered-by-success)))

(defn- step-producer-returning-with-first-successful [args steps-and-ids]
  (let [step-result-channels (map #(async/go (core/execute-step args %)) steps-and-ids)
        result (wait-for-success-on step-result-channels)]
    (if (nil? result)
      [{:status :failure}]
      [result])))

(defn ^{:display-type :parallel} either [& steps]
  (fn [args ctx]
    (let [kill-switch (atom false)
          execute-output (core/execute-steps steps args ctx
                                                     :is-killed kill-switch
                                                     :step-result-producer step-producer-returning-with-first-successful)]
      (reset! kill-switch true)
      (if (= :success (:status execute-output))
        (first (vals (:outputs execute-output)))
        execute-output))))

(defn- parallel-step-result-producer [args steps-and-ids]
  (pmap #(core/execute-step args %) steps-and-ids))

(defn- execute-steps-in-parallel [steps args ctx]
  (core/execute-steps steps args ctx
                      :step-result-producer parallel-step-result-producer))

(defn ^{:display-type :parallel} in-parallel [& steps]
  (fn [args ctx]
    (post-process-container-results
      (execute-steps-in-parallel steps args ctx))))


(defn ^{:display-type :container} in-cwd [cwd & steps]
  (fn [args ctx]
    (post-process-container-results
      (core/execute-steps steps (assoc args :cwd cwd) ctx))))

(defn ^{:display-type :container} run [ & steps]
  (fn [args ctx]
    (post-process-container-results
      (core/execute-steps steps args ctx))))