(ns lambdacd.steps.result
  (:require [lambdacd.steps.status :as status]
            [clojure.string :as s]
            [lambdacd.util :as utils]
            [lambdacd.stepresults.flatten :as flatten]
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

(defn- resolve-first-matching [resolvers]
  (fn [k v1 v2]
    (->> resolvers
         (map #(% k v1 v2))
         (filter (complement nil?))
         (first))))

(defn merge-two-step-results [a b & {:keys [resolvers]
                                     :or   {resolvers [merge-resolvers/status-resolver
                                                       merge-resolvers/merge-nested-maps-resolver
                                                       merge-resolvers/second-wins-resolver]}}]
  (utils/merge-with-k-v (resolve-first-matching resolvers) a b))

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
