(ns lambdacd.steps.result
  (:require [lambdacd.stepresults.flatten :as flatten]
            [lambdacd.stepresults.merge :as merge]
            [lambdacd.stepresults.merge-resolvers :as merge-resolvers]))

; --- common ---
(defn merge-nested-maps-resolver
  "DEPRECATED, use `lambdacd.stepresults.merge-resolvers/merge-nested-maps-resolver` instead."
  {:deprecated "0.13.1"}
  [k v1 v2]
  (merge-resolvers/merge-nested-maps-resolver k v1 v2))

(defn status-resolver
  "DEPRECATED, use `lambdacd.stepresults.merge-resolvers/status-resolver` instead."
  {:deprecated "0.13.1"}
  [k v1 v2]
  (merge-resolvers/status-resolver k v1 v2))

(defn second-wins-resolver
  "DEPRECATED, use `lambdacd.stepresults.merge-resolvers/second-wins-resolver` instead."
  {:deprecated "0.13.1"}
  [k v1 v2]
  (merge-resolvers/second-wins-resolver k v1 v2))

(defn combine-to-list-resolver
  "DEPRECATED, use `lambdacd.stepresults.merge-resolvers/combine-to-list-resolver` instead."
  {:deprecated "0.13.1"}
  [k v1 v2]
  (merge-resolvers/combine-to-list-resolver k v1 v2))

(defn join-output-resolver
  "DEPRECATED, use `lambdacd.stepresults.merge-resolvers/join-output-resolver` instead."
  {:deprecated "0.13.1"}
  [k v1 v2]
  (merge-resolvers/join-output-resolver k v1 v2))


(defn merge-two-step-results
  "DEPRECATED, use `lambdacd.stepresults.merge/merge-two-step-results` instead."
  {:deprecated "0.13.1"}
  [a b & resolvers]
  (apply merge/merge-two-step-results a b resolvers))

(defn merge-step-results
  "DEPRECATED, use `lambdacd.stepresults.merge/merge-step-results` instead."
  {:deprecated "0.13.1"}
  [step-results merge-two-results-fn] ; TODO: test this!
  (merge/merge-step-results step-results merge-two-results-fn))

(defn flatten-step-result-outputs
  "DEPRECATED, use `lambdacd.stepresults.flatten/flatten-step-result-outputs` instead."
  {:deprecated "0.13.1"}
  [outputs]
  (flatten/flatten-step-result-outputs outputs))
