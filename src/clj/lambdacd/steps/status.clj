(ns lambdacd.steps.status
  (:require [lambdacd.util :as util]))

(defn- all [statuses status]
  (every? #(= % status) statuses))

(defn- one-in [statuses status]
  (util/contains-value? status statuses))

(defn successful-when-one-successful [statuses]
  (let [has-failed (util/contains-value? :failure statuses)
        has-running (util/contains-value? :running statuses)
        all-waiting (all statuses :waiting)
        all-killed (all statuses :killed)
        one-ok (util/contains-value? :success statuses)]
    (cond
      has-failed :failure
      one-ok :success
      has-running :running
      all-waiting :waiting
      all-killed :killed
      :else :unknown)))

(defn successful-when-all-successful [statuses]
  (cond
    (one-in statuses :failure) :failure
    (one-in statuses :running) :running
    (one-in statuses :waiting) :waiting
    (all    statuses :success) :success
    :else                      :unknown))
