(ns lambdacd.steps.status
  (:require [lambdacd.util.internal.map :as map-util]))

(defn- all [statuses status]
  (every? #(= % status) statuses))

(defn- one-in [statuses status]
  (map-util/contains-value? status statuses))

(defn successful-when-one-successful [statuses]
  (cond
    (all statuses :failure) :failure
    (all statuses :killed) :killed
    (one-in statuses :success) :success
    (one-in statuses :running) :running
    (one-in statuses :waiting) :waiting
    :else :unknown))

(defn successful-when-all-successful [statuses]
  (cond
    (one-in statuses :running) :running
    (one-in statuses :waiting) :waiting
    (one-in statuses :failure) :failure
    (all    statuses :success) :success
    :else                      :unknown))

(defn choose-last-or-not-success [s1 s2]
  (if (= s1 :success)
    s2
    (if (= s2 :success)
      s1
      s2)))
