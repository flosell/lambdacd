(ns lambdacd.presentation.pipeline-state
  "This namespace contains functions useful to convert the internal state of a pipeline into something more easy to use
  for further processing, e.g. to present it to a user.

  Functions taking a map step-id to step-result are usually called with the result of `lambdacd.state.core/get-step-results`"
  (:require [clj-time.core :as t]
            [clojure.tools.logging :as log]
            [clj-timeframes.core :as tf]
            [lambdacd.step-id :as step-id]
            [lambdacd.state.core :as state]))

(defn- desc [a b]
  (compare b a))

(defn- asc [a b]
  (compare a b))

(defn- root-step? [[step-id _]]
  (step-id/root-step-id? step-id))

(defn- root-step-id [[step-id _]]
  (step-id/root-step-id-of step-id))

(defn- step-result [[_ step-result]]
  step-result)

(defn overall-build-status
  "Takes a map of step-ids to step results and returns the status of the pipeline as a whole:
  ```clojure
  > (overall-build-status {'(0) {:status :success :most-recent-update-at stop-time :first-updated-at start-time}
                           '(1) {:status :running :most-recent-update-at stop-time :first-updated-at start-time}})
  :running
  ```"
  [step-ids-and-results]
  (let [accumulated-status (->> step-ids-and-results
                                (filter root-step?)
                                (sort-by root-step-id)
                                (last)
                                (step-result)
                                (:status))]
    (or accumulated-status :unknown)))

(defn- not-waiting? [result]
  (not (:has-been-waiting result)))

(defn not-retriggered?
  "DEPRECATED"
  {:deprecated "0.13.1"}
  [result]
  (not (:retrigger-mock-for-build-number result)))

(defn- first-with-key-ordered-by [comp key steps]
  (->> steps
       (filter not-waiting?)
       (map key)
       (sort comp)
       (first)))

(defn earliest-first-update
  "Takes a map of step-ids to step results and finds the earliest timestamp for `:first-updated-at`, usually the start time of a pipeline"
  [step-ids-and-results]
  (->> (vals step-ids-and-results)
       (filter not-retriggered?)
       (first-with-key-ordered-by asc :first-updated-at)))

(defn latest-most-recent-update
  "Takes a map of step-ids to step results and finds the latest timestamp for `:most-recent-update-at`, usually the end or most recent update of a pipeline"
  [step-ids-and-results]
  (first-with-key-ordered-by desc :most-recent-update-at (vals step-ids-and-results)))

(defn build-that-was-retriggered
  "Takes a map of step-ids to step results and returns `nil` (for normal builds) or the build number of the build that was retriggered."
  [step-ids-and-results]
  (get-in step-ids-and-results [[1] :retrigger-mock-for-build-number]))

(defn- build-interval [step]
  (try
    (t/interval (:first-updated-at step) (:most-recent-update-at step))
    (catch IllegalArgumentException e
      (log/warn (str "Timestamps for build duration dont add up (" (.getMessage e) "), falling back to duration 0. responsible step-result:" step))
      (t/interval (t/epoch) (t/epoch)))))

(defn- interval-duration [interval]
  (t/in-seconds interval))

(defn build-duration
  "Takes a map of step-ids to step results and returns the duration of a build in seconds (excluding time the build spent waiting)"
  [step-ids-and-results]
  (->> step-ids-and-results
       (vals)
       (filter not-waiting?)
       (map build-interval)
       (tf/merge-intervals)
       (map interval-duration)
       (reduce +)))

(defn- history-entry
  ([build-number step-ids-and-results metadata]
   {:build-number          build-number
    :status                (overall-build-status step-ids-and-results)
    :most-recent-update-at (latest-most-recent-update step-ids-and-results)
    :first-updated-at      (earliest-first-update step-ids-and-results)
    :retriggered           (build-that-was-retriggered step-ids-and-results)
    :duration-in-sec       (build-duration step-ids-and-results)
    :build-metadata        metadata})
  ([[build-number step-ids-and-results]]
   (history-entry build-number step-ids-and-results {})))

(defn- legacy-history-for [state]
  (sort-by :build-number (map history-entry state)))

(defn- history-for-pipeline-state-component [ctx]
  (for [build-number (state/all-build-numbers ctx)]
    (history-entry build-number (state/get-step-results ctx build-number) (state/get-build-metadata ctx build-number))))

(defn history-for
  "Returns a build history for a given ctx;
  Calling this with a complete build state (the get-all-result) is now DEPRECATED.

  Example:
  ```clojure
  > (history-for ctx)
  [{:build-number          8
    :status                :waiting
    :most-recent-update-at stop-time
    :first-updated-at      start-time
    :retriggered           nil
    :duration-in-sec       10
    :build-metadata {:some :metadata}}
   {:build-number          9
    :status                :running
    :most-recent-update-at stop-time
    :first-updated-at      start-time
    :retriggered           2
    :duration-in-sec       10
    :build-metadata {}}]
  ```"
  [all-state-or-ctx]
  (if (:pipeline-state-component all-state-or-ctx)
    (history-for-pipeline-state-component all-state-or-ctx)
    (legacy-history-for all-state-or-ctx)))

(defn most-recent-step-result-with
  "Searches the build history for the current build-step (as denoted by the `:step-id` in `ctx`) for a step result with a particular key and returns the complete step result."
  [key ctx]
  (->> (state/all-build-numbers ctx)
       (reverse)
       (map #(state/get-step-result ctx % (:step-id ctx)))
       (filter key)
       (first)))
