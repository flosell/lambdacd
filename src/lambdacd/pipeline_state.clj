(ns lambdacd.pipeline-state
  "responsible to manage the current state of the pipeline
  i.e. what's currently running, what are the results of each step, ..."
  (:require [clojure.core.async :as async]
            [lambdacd.util :as util]))

(def initial-pipeline-state {})

(defn update [{step-id :step-id state :_pipeline-state} step-result]
  (if (not (nil? state)) ; convenience for tests: if no state exists we just do nothing
    (swap! state #(assoc %1 step-id step-result))))

(defn running [ctx]
  (update ctx {:status :running}))
