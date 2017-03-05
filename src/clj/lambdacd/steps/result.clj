(ns lambdacd.steps.result
  (:require [lambdacd.steps.status :as status]
            [clojure.string :as s]
            [lambdacd.util :as utils]
            [lambdacd.stepresults.flatten :as flatten]
            [lambdacd.stepresults.merge :as merge]))

; --- common ---
(defn merge-nested-maps-resolver [_ v1 v2]
  (when (and (map? v1) (map? v2))
    (merge v1 v2)))

(defn status-resolver [k v1 v2]
  (when (= k :status)
    (status/choose-last-or-not-success v1 v2)))

(defn second-wins-resolver [_ _ v2]
  v2)

(defn combine-to-list-resolver [_ v1 v2]
  (cond
    (and (coll? v1) (coll? v2)) (into v1 v2)
    (coll? v1) (merge v1 v2)
    :else nil))

(defn join-output-resolver [k v1 v2]
  (when (and (= :out k)
             (string? v1)
             (string? v2))
    (s/join "\n" [v1 v2])))

(defn- resolve-first-matching [resolvers]
  (fn [k v1 v2]
    (->> resolvers
         (map #(% k v1 v2))
         (filter (complement nil?))
         (first))))

(defn merge-two-step-results [a b & {:keys [resolvers]
                                     :or   {resolvers [status-resolver
                                                       merge-nested-maps-resolver
                                                       second-wins-resolver]}}]
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
