(ns lambdacd.steps.result
  (:require [lambdacd.steps.status :as status]
            [clojure.string :as s]
            [lambdacd.util :as utils]))

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

(defn merge-step-results [step-results merger] ; TODO: test this!
  (reduce merger {} step-results))

(defn flatten-step-result-outputs [outputs]
  (into {}
        (for [[k v] outputs]
          (if (:outputs v)
            (assoc (flatten-step-result-outputs (:outputs v)) k v)
            {k v}))))
