(ns lambdacd.pipeline-state
  "responsible to manage store the current state of the pipeline
  i.e. what's currently running, what are the results of each step, ..."
  (:require [clojure.core.async :as async]
            [lambdacd.util :as util]))

(def initial-pipeline-state {:results {}})
(def pipeline-state (atom initial-pipeline-state))

(defn get-pipeline-state []
  @pipeline-state)

(defn reset-pipeline-state []
  (reset! pipeline-state initial-pipeline-state))

(defn update-pipeline-state [step-id step-result pipeline-state]
  (let [cur-results  (:results pipeline-state)
        new-results  (assoc cur-results step-id step-result)]
    {:results new-results}))

(defn set-running! [step-id]
  (swap! pipeline-state (partial update-pipeline-state step-id {:status :running})))

(defn set-finished! [step-id step-result]
  (swap! pipeline-state (partial update-pipeline-state step-id step-result)))

