(ns lambdacd.internal.default-pipeline-state
  "responsible to manage the current state of the pipeline
  i.e. what's currently running, what are the results of each step, ..."
  (:require [lambdacd.internal.default-pipeline-state-persistence :as persistence]
            [clj-time.core :as t]
            [clojure.core.async :as async]
            [lambdacd.internal.pipeline-state :as pipeline-state-protocol]
            [lambdacd.event-bus :as event-bus]
            [lambdacd.util :as util]))

(def clean-pipeline-state {})

(defn initial-pipeline-state [{ home-dir :home-dir }]
  (persistence/read-build-history-from home-dir))

(defn- put-if-not-present [m k v]
  (if (contains? m k)
    m
    (assoc m k v)))

(defn- update-current-run [step-id step-result current-state]
  (let [current-step-result (get current-state step-id)
        now (t/now)
        new-step-result (-> current-step-result
                            (assoc :most-recent-update-at now)
                            (put-if-not-present :first-updated-at now)
                            (merge step-result))]
    (assoc current-state step-id new-step-result)))

(defn- update-pipeline-state [build-number step-id step-result current-state]
  (assoc current-state build-number (update-current-run step-id step-result (get current-state build-number))))

(defn- most-recent-build-number-in-state [pipeline-state]
  (if-let [current-build-number (last (sort (keys pipeline-state)))]
    current-build-number
    0))

(defn next-build-number-legacy [state]
  (inc (most-recent-build-number-in-state @state)))

(defn update-legacy
  [build-number step-id step-result home-dir state]
  (if (not (nil? state)) ; convenience for tests: if no state exists we just do nothing
    (let [new-state (swap! state (partial update-pipeline-state build-number step-id step-result))]
      (persistence/write-build-history home-dir build-number new-state))))


(defrecord DefaultPipelineState [state-atom home-dir]
  pipeline-state-protocol/PipelineStateComponent
  (update [self build-number step-id step-result]
    (update-legacy build-number step-id step-result home-dir state-atom))
  (get-all [self]
    @state-atom)
  (get-internal-state [self]
    state-atom)
  (next-build-number [self]
    (next-build-number-legacy state-atom)))

(defn- start-pipeline-state-updater [instance ctx] ; TODO could be generalized for others to use
  (let [subscription (event-bus/subscribe ctx :step-result-updated)
        step-updates-channel (util/buffered (event-bus/only-payload subscription))]
    (async/go-loop []
      (if-let [step-result-update (async/<! step-updates-channel)]
        (let [step-result (:step-result step-result-update)
              build-number (:build-number step-result-update)
              step-id (:step-id step-result-update)]
          (pipeline-state-protocol/update instance build-number step-id step-result)
          (recur))))))

(defn new-default-pipeline-state [state-atom config ctx]
  (let [home-dir (:home-dir config)
        instance (->DefaultPipelineState state-atom home-dir)]
    (start-pipeline-state-updater instance ctx)
    instance))