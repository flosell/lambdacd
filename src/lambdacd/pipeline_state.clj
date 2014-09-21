(ns lambdacd.pipeline-state
  "responsible to manage the current state of the pipeline
  i.e. what's currently running, what are the results of each step, ..."
  (:require [clojure.core.async :as async]
            [lambdacd.util :as util]))

(def initial-pipeline-state {})
(def pipeline-state (atom initial-pipeline-state))

(defn get-pipeline-state []
  @pipeline-state)

(defn reset-pipeline-state []
  (reset! pipeline-state initial-pipeline-state))


(defn update [{step-id :step-id} step-result]
  (swap! pipeline-state #(assoc %1 step-id step-result)))

(defn running [ctx]
  (update ctx {:status :running}))
