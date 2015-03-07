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
