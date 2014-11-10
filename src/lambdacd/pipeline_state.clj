(ns lambdacd.pipeline-state
  "responsible to manage the current state of the pipeline
  i.e. what's currently running, what are the results of each step, ..."
  (:require [clojure.core.async :as async]
            [lambdacd.util :as util]
            [clojure.data.json :as json]))

(def initial-pipeline-state {})

(defn- update-current-run [step-id step-result current-state]
  (assoc current-state step-id step-result))

(defn- update-pipeline-state [build-number step-id step-result current-state]
  (assoc current-state build-number (update-current-run step-id step-result (get current-state build-number))))

(defn- write-state-to-disk [home-dir new-state]
  (if home-dir
  (let [path (str home-dir "/" "history.json")]
    (util/write-as-json path new-state))))

(defn update [{step-id :step-id state :_pipeline-state build :build-number { home-dir :home-dir } :config } step-result]
  (if (not (nil? state)) ; convenience for tests: if no state exists we just do nothing
    (let [new-state (swap! state (partial update-pipeline-state build step-id step-result))]
      (write-state-to-disk home-dir new-state))))

(defn running [ctx]
  (update ctx {:status :running}))
