(ns lambdacd.internal.pipeline-state
  "responsible to manage the current state of the pipeline
  i.e. what's currently running, what are the results of each step, ..."
  (:require [lambdacd.internal.pipeline-state-persistence :as persistence]
            [clj-time.core :as t]))

(def clean-pipeline-state {})

(defn initial-pipeline-state [{ home-dir :home-dir }]
  (persistence/read-build-history-from home-dir))

(defn- put-if-not-present [m k v]
  (if (contains? m k)
    m
    (assoc m k v)))

(defn- update-current-run [step-id step-result current-state]
  (let [current-step-result (get current-state step-id)
        new-step-result (-> current-step-result
                            (assoc :most-recent-update-at (t/now))
                            (put-if-not-present :first-updated-at (t/now))
                            (merge step-result))]
    (assoc current-state step-id new-step-result)))

(defn- update-pipeline-state [build-number step-id step-result current-state]
  (assoc current-state build-number (update-current-run step-id step-result (get current-state build-number))))

(defn- most-recent-build-number-in-state [pipeline-state]
  (if-let [current-build-number (last (sort (keys pipeline-state)))]
    current-build-number
    0))

(defn next-build-number [{pipeline-state :_pipeline-state }]
  (inc (most-recent-build-number-in-state @pipeline-state)))

(defn finished-step? [step-result]
  (let [status (:status step-result)
        is-waiting (= :waiting status)
        is-running (= :running status)]
  (not (or is-waiting is-running))))

(defn- finished-step-count-in [build]
  (let [results (vals build)
        finished-steps (filter finished-step? results)
        finished-step-count (count finished-steps)]
    finished-step-count))

(defn- call-callback-when-most-recent-build-running [callback key reference old new]
  (let [cur-build-number (most-recent-build-number-in-state new)
        cur-build (get new cur-build-number)
        old-cur-build (get old cur-build-number)
        finished-step-count-new (finished-step-count-in cur-build)
        finished-step-count-old (finished-step-count-in old-cur-build)
        first-step-in-current-build (get cur-build `(1))
        is-retrigger-mock (contains? first-step-in-current-build :retrigger-mock-for-build-number)]
    (if (and
          (not (nil? first-step-in-current-build))
          (= 1 finished-step-count-new)
          (not= 1 finished-step-count-old)
          (not is-retrigger-mock))
      (callback))))

(defn notify-when-most-recent-build-running [{pipeline-state :_pipeline-state} callback]
  (add-watch pipeline-state :notify-most-recent-build-running (partial call-callback-when-most-recent-build-running callback)))


(defn update [{step-id :step-id state :_pipeline-state build-number :build-number { home-dir :home-dir } :config } step-result]
  (if (not (nil? state)) ; convenience for tests: if no state exists we just do nothing
    (let [new-state (swap! state (partial update-pipeline-state build-number step-id step-result))]
      (persistence/write-build-history home-dir build-number new-state))))

(defn running [ctx]
  (update ctx {:status :running}))


