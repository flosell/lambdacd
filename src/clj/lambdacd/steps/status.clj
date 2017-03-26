(ns lambdacd.steps.status
  "DEPRECATED, use `lambdacd.stepstatus.predicates` and `lambdacd.stepstatus.unify` instead."
  {:deprecated "0.13.1"}
  (:require [lambdacd.stepstatus.predicates :as predicates]
            [lambdacd.stepstatus.unify :as unify]))

(defn successful-when-one-successful
  "DEPRECATED, use `lambdacd.stepstatus.unify/successful-when-one-successful` instead"
  {:deprecated "0.13.1"}
  [statuses]
  (unify/successful-when-one-successful statuses))

(defn successful-when-all-successful
  "DEPRECATED, use `lambdacd.stepstatus.unify/successful-when-all-successful` instead"
  {:deprecated "0.13.1"}
  [statuses]
  (unify/successful-when-all-successful statuses))

(defn choose-last-or-not-success
  "DEPRECATED"
  {:deprecated "0.13.1"}
  [s1 s2]
  (if (= s1 :success)
    s2
    (if (= s2 :success)
      s1
      s2)))

(defn is-active?
  "DEPRECATED, use `lambdacd.stepstatus.predicates/is-active?` instead"
  {:deprecated "0.13.1"}
  [status]
  (predicates/is-active? status))
