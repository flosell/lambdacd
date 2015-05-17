(ns lambdacd.presentation.pipeline-state
  (:require [lambdacd.util :as util]))

(defn- desc [a b]
  (compare b a))

(defn- asc [a b]
  (compare a b))

(defn- root-step? [[step-id _]]
  (= 1 (count step-id)))

(defn- get-step-id [[step-id _]]
  step-id)

(defn- get-step-result [[_ step-result]]
  step-result)

(defn- status-for-steps [step-ids-and-results]
  (let [accumulated-status (->> step-ids-and-results
                              (filter root-step?)
                              (sort-by get-step-id desc)
                              (first)
                              (get-step-result)
                              (:status))]
    (or accumulated-status :unknown)))

(defn- not-waiting? [result]
  (not (:has-been-waiting result)))

(defn- first-with-key-ordered-by [steps comp key]
  (->> steps
       (filter not-waiting?)
       (map key)
       (sort comp)
       (first)))

(defn- earliest-first-update [steps]
  (first-with-key-ordered-by steps asc :first-updated-at))

(defn- latest-most-recent-update [steps]
  (first-with-key-ordered-by steps desc :most-recent-update-at))

(defn- history-entry [[build-number step-ids-and-results]]
  (let [step-results (vals step-ids-and-results)]
    {:build-number build-number
     :status (status-for-steps step-ids-and-results)
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
