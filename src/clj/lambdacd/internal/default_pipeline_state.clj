(ns lambdacd.internal.default-pipeline-state
  "responsible to manage the current state of the pipeline
  i.e. what's currently running, what are the results of each step, ..."
  (:require [lambdacd.internal.default-pipeline-state-persistence :as persistence]
            [clj-time.core :as t]
            [lambdacd.internal.pipeline-state :as pipeline-state]
            [lambdacd.util :as util]))

(def clean-pipeline-state {})

(defn initial-pipeline-state [{home-dir :home-dir}]
  (persistence/read-build-history-from home-dir))

(defn- update-current-run [step-id step-result current-state]
  (let [current-step-result (get current-state step-id)
        now                 (t/now)
        new-step-result     (-> current-step-result
                                (assoc :most-recent-update-at now)
                                (util/put-if-not-present :first-updated-at now)
                                (merge step-result))]
    (assoc current-state step-id new-step-result)))

(defn- update-step-result-in-state [build-number step-id step-result current-state]
  (let [build     (get current-state build-number)
        build-new (update-current-run step-id step-result build)
        state-new (assoc current-state build-number build-new)]
    state-new))

(defn- truncate-build-history [max-builds state]
  (->> state
       (sort-by key >)
       (take max-builds)
       (into {})))

(defn- most-recent-build-number-in-state [pipeline-state]
  (if-let [current-build-number (last (sort (keys pipeline-state)))]
    current-build-number
    0))

(defrecord DefaultPipelineState [state-atom home-dir max-builds]
  pipeline-state/PipelineStateComponent
  (update [self build-number step-id step-result]
    (if (not (nil? state-atom))                             ; convenience for tests: if no state exists we just do nothing
      (let [new-state (swap! state-atom #(->> %
                                              (update-step-result-in-state build-number step-id step-result)
                                              (truncate-build-history max-builds)))]
        (persistence/write-build-history home-dir build-number new-state)
        (persistence/clean-up-old-history home-dir new-state))))
  (get-all [self]
    @state-atom)
  (get-internal-state [self]
    state-atom)
  (next-build-number [self]
    (inc (most-recent-build-number-in-state @state-atom))))

(defn new-default-pipeline-state [config & {:keys [initial-state-for-testing]}]
  (let [state-atom (atom (or initial-state-for-testing (initial-pipeline-state config)))
        home-dir   (:home-dir config)
        max-builds (or (:max-builds config) Integer/MAX_VALUE)
        instance   (->DefaultPipelineState state-atom home-dir max-builds)]
    instance))
