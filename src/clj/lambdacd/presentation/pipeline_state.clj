(ns lambdacd.presentation.pipeline-state
  (:require [lambdacd.util :as util]))


(defn- status-for-steps [steps]
  (let [statuses (map :status (vals steps))
        has-failed (util/contains-value? :failure statuses)
        has-running (util/contains-value? :running statuses)
        has-waiting (util/contains-value? :waiting statuses)
        all-ok (every? #(= % :ok) statuses)]
    (cond
      has-failed :failure
      has-running :running
      all-ok :ok
      has-waiting :waiting
      :else :unknown)))

(defn- history-entry [[k v]]
  { :build-number k
   :status (status-for-steps v)})

(defn history-for [state]
  (map history-entry state))

(defn most-recent-build-number-in [state]
  (apply max (keys state)))


(defn most-recent-step-result-with [key ctx]
  (let [state (deref (:_pipeline-state ctx))
        step-id (:step-id ctx)
        step-results (map second (reverse (sort-by first (seq state))))
        step-results-for-id (map #(get % step-id) step-results)
        step-results-with-key (filter key step-results-for-id)]
    (first step-results-with-key)))
