(ns lambdacd.steps.status
  (:require [lambdacd.util :as util]))

(defn- all [statuses status]
  (every? #(= % status) statuses))

(defn- one-in [statuses status]
  (util/contains-value? status statuses))

(defn successful-when-one-successful [statuses]
  (let [none-failures (filter #(not= :failure %) statuses)]
    (cond
      (all    statuses      :failure) :failure
      (one-in statuses      :success) :success
      (one-in statuses      :running) :running
      (all    none-failures :waiting) :waiting
      (all    statuses      :killed)  :killed
      :else :unknown)))

(defn successful-when-all-successful [statuses]
  (cond
    (one-in statuses :failure) :failure
    (one-in statuses :running) :running
    (one-in statuses :waiting) :waiting
    (all    statuses :success) :success
    :else                      :unknown))
