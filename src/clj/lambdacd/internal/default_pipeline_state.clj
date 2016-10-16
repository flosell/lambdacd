(ns lambdacd.internal.default-pipeline-state
  "responsible to manage the current state of the pipeline
  i.e. what's currently running, what are the results of each step, ..."
  (:require [lambdacd.internal.default-pipeline-state-persistence :as persistence]
            [clj-time.core :as t]
            [lambdacd.internal.pipeline-state :as old-pipeline-state]
            [lambdacd.state.protocols :as protocols]
            [lambdacd.util :as util]
            [clojure.data :as data]))

(def clean-pipeline-state {})

(defn initial-pipeline-state [{home-dir :home-dir}]
  (persistence/read-build-history-from home-dir))

(defn- update-step-result [new-step-result current-step-result]
  (let [now                       (t/now)]
    (-> new-step-result
        (assoc :most-recent-update-at now)
        (assoc :first-updated-at (or (:first-updated-at current-step-result) now))
        (merge new-step-result))))

(defn- update-step-result-in-state [build-number step-id new-step-result current-state]
  (update-in current-state [build-number step-id] #(update-step-result new-step-result %)))

(defn- truncate-build-history [home-dir max-builds state]
  (let [new-state (->> state
                       (sort-by key >)
                       (take max-builds)
                       (into {}))
        [only-in-old _ _] (data/diff (set (keys state)) (set (keys new-state)))]
    (persistence/clean-up-old-builds home-dir only-in-old)
    new-state))

(defn- most-recent-build-number-in-state [pipeline-state]
  (if-let [current-build-number (last (sort (keys pipeline-state)))]
    current-build-number
    0))

(defrecord DefaultPipelineState [state-atom structure-atom home-dir max-builds]
  old-pipeline-state/PipelineStateComponent
  (update [self build-number step-id new-step-result]
    (protocols/consume-step-result-update self build-number step-id new-step-result))
  (get-all [self]
    @state-atom)
  (get-internal-state [self]
    state-atom)
  (next-build-number [self]
    (inc (most-recent-build-number-in-state @state-atom)))

  protocols/PipelineStructureConsumer
  (consume-pipeline-structure [self build-number pipeline-structure-representation]
    (swap! structure-atom #(assoc % build-number pipeline-structure-representation))
    (persistence/write-pipeline-structure home-dir build-number pipeline-structure-representation))
  protocols/PipelineStructureSource
  (get-pipeline-structure [self build-number]
    (get @structure-atom build-number))

  protocols/StepResultUpdateConsumer
  (consume-step-result-update [self build-number step-id step-result]
    (let [new-state (swap! state-atom #(->> %
                                            (update-step-result-in-state build-number step-id step-result)
                                            (truncate-build-history home-dir max-builds)))]
      (persistence/write-build-history home-dir build-number new-state)))
  protocols/QueryStepResultsSource
  (get-step-results [self build-number]
    (get @state-atom build-number)))

(defn new-default-pipeline-state [config & {:keys [initial-state-for-testing]}]
  (let [home-dir   (:home-dir config)
        state-atom (atom (or initial-state-for-testing (initial-pipeline-state config)))
        structure-atom (atom (persistence/read-pipeline-structures home-dir))
        max-builds (or (:max-builds config) Integer/MAX_VALUE)
        instance   (->DefaultPipelineState state-atom structure-atom home-dir max-builds)]
    instance))
