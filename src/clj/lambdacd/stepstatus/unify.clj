(ns lambdacd.stepstatus.unify
  "Functions that implement logic to summarize a list of statuses into a single status.
  Commonly used in combination with `unify-only-status` as a `:unify-results-fn` to generate the status of a parent step from the statuses of child steps.
  These functions might return `:unknown` if a combination of statuses does not make sense to their scenario."
  (:require [lambdacd.util.internal.map :as map-util]))

(defn- all [statuses status]
  (every? #(= % status) statuses))

(defn- one-in [statuses status]
  (map-util/contains-value? status statuses))

(defn successful-when-one-successful
  "Summarizes the given statuses optimistically, e.g. returns `:success` if even one status is success or `:running` as long as a single status is `:running`.

  Used for scenarios where we don't expect all steps to succeed, e.g. the `either` control-flow."
  [statuses]
  (cond
    (all statuses :failure) :failure
    (all statuses :killed) :killed
    (one-in statuses :success) :success
    (one-in statuses :running) :running
    (one-in statuses :waiting) :waiting
    :else :unknown))

(defn successful-when-all-successful
  "Summarizes the given statuses pessimistically, e.g. returns `:success` only if all statuses were success and `:failure` if only a single status is `:failure`.

  Used for scenarios where we expect all steps to succeeed, e.g. `in-parallel` control-flow. Also used as the default in `execute-steps`."
  [statuses]
  (cond
    (one-in statuses :running) :running
    (one-in statuses :waiting) :waiting
    (one-in statuses :failure) :failure
    (all    statuses :success) :success
    :else                      :unknown))
