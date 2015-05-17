(ns lambdacd.presentation.pipeline-state
  (:require [lambdacd.util :as util]))


(defn- status-for-steps [steps]
  (let [statuses (map :status steps)
        statuses-not-killed (filter #(not= :killed %) statuses)
        has-failed (util/contains-value? :failure statuses)
        has-running (util/contains-value? :running statuses)
        has-waiting (util/contains-value? :waiting statuses)
        all-ok (every? #(= % :success) statuses-not-killed)]
    (cond
      has-running :running
      has-failed :failure
      all-ok :success
      has-waiting :waiting
      :else :unknown)))

(defn- latest-first [a b]
  (compare b a))

(defn- earliest-first [a b]
  (compare a b))

(defn- not-waiting? [result]
  (not (:has-been-waiting result)))

(defn- first-with-key-ordered-by [steps comp key]
  (->> steps
       (filter not-waiting?)
       (map key)
       (sort comp)
       (first)))

(defn- earliest-first-update [steps]
  (first-with-key-ordered-by steps earliest-first :first-updated-at))

(defn- latest-most-recent-update [steps]
  (first-with-key-ordered-by steps latest-first :most-recent-update-at))

(defn- history-entry [[build-number step-ids-and-results]]
  (let [step-results (vals step-ids-and-results)]
    {:build-number build-number
     :status (status-for-steps step-results)
     :most-recent-update-at (latest-most-recent-update step-results)
     :first-updated-at (earliest-first-update step-results)}))

(defn history-for [state]
  (sort-by :build-number (map history-entry state)))

(defn most-recent-build-number-in [state]
  (apply max (keys state)))


(defn most-recent-step-result-with [key ctx]
  (let [state (deref (:_pipeline-state ctx))
        step-id (:step-id ctx)
        step-results (map second (reverse (sort-by first (seq state))))
        step-results-for-id (map #(get % step-id) step-results)
        step-results-with-key (filter key step-results-for-id)]
    (first step-results-with-key)))
