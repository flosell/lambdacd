(ns lambdacd.state.internal.dead-steps-marking
  (:require [lambdacd.internal.running-builds-tracking :as running-builds-tracking]
            [lambdacd.stepstatus.predicates :as predicates]))

(defn- old-status-or-dead [ctx build-number step-id status]
  (if (and (not (running-builds-tracking/is-running? ctx build-number step-id))
           (predicates/is-active? status))
    :dead
    status))

(defn- mark-dead-step [ctx build-number [step-id step-result]]
  [step-id (update step-result :status #(old-status-or-dead ctx build-number step-id %))])

(defn mark-dead-steps [ctx build-number step-results]
  (if step-results
    (->> step-results
         (map #(mark-dead-step ctx build-number %1))
         (into {}))))
