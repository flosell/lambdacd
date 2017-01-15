(ns lambdacd.presentation.pipeline-state
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

(defn overall-build-status [step-ids-and-results]
  (let [accumulated-status (->> step-ids-and-results
                                (filter root-step?)
                                (sort-by root-step-id)
                                (last)
                                (step-result)
                                (:status))]
    (or accumulated-status :unknown)))

(defn- not-waiting? [result]
  (not (:has-been-waiting result)))

(defn not-retriggered? [result]
  (not (:retrigger-mock-for-build-number result)))

(defn- first-with-key-ordered-by [comp key steps]
  (->> steps
       (filter not-waiting?)
       (map key)
       (sort comp)
       (first)))

(defn earliest-first-update [step-ids-and-results]
  (->> (vals step-ids-and-results)
       (filter not-retriggered?)
       (first-with-key-ordered-by asc :first-updated-at)))

(defn latest-most-recent-update [step-ids-and-results]
  (first-with-key-ordered-by desc :most-recent-update-at (vals step-ids-and-results)))

(defn build-that-was-retriggered [step-ids-and-results]
  (get-in step-ids-and-results [[1] :retrigger-mock-for-build-number]))

(defn- build-interval [step]
  (try
    (t/interval (:first-updated-at step) (:most-recent-update-at step))
    (catch IllegalArgumentException e
      (log/warn (str "Timestamps for build duration dont add up (" (.getMessage e) "), falling back to duration 0. responsible step-result:" step))
      (t/interval (t/epoch) (t/epoch)))))

(defn- interval-duration [interval]
  (t/in-seconds interval))

(defn build-duration [step-ids-and-results]
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
  "Returns a build history for a given build state (the result of get-all) or a ctx;
  Calling this with a complete build state (the get-all-result) is now DEPRECATED"
  [all-state-or-ctx]
  (if (:pipeline-state-component all-state-or-ctx)
    (history-for-pipeline-state-component all-state-or-ctx)
    (legacy-history-for all-state-or-ctx)))

(defn most-recent-step-result-with [key ctx]
  (->> (state/all-build-numbers ctx)
       (reverse)
       (map #(state/get-step-result ctx % (:step-id ctx)))
       (filter key)
       (first)))
