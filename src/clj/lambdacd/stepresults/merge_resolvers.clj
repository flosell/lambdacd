(ns lambdacd.stepresults.merge-resolvers
  "Contains functions that can act as resolvers in merge-two-step-results
  by returning a merged result of two values if they match or nil if they don't."
  (:require [clojure.string :as s]
            [lambdacd.steps.status :as status]))

(defn- choose-last-or-not-success
  ([s1 s2]
   (if (= s1 :success)
     s2
     (if (= s2 :success)
       s1
       s2))))

(defn merge-nested-maps-resolver
  "Resolver that merges two given maps with the default clojure `merge`."
  [_ v1 v2]
  (when (and (map? v1) (map? v2))
    (merge v1 v2)))

(defn status-resolver
  "Resolver that resolves only the :status key with the `last-or-not-success` function."
  [k v1 v2]
  (when (= k :status)
    (choose-last-or-not-success v1 v2)))

(defn second-wins-resolver
  "Resolver that always returns the second (usually newer) value."
  [_ _ v2]
  v2)

(defn combine-to-list-resolver
  "Resolver that concatenates two list values or a value onto an existing list."
  [_ v1 v2]
  (cond
    (and (coll? v1) (coll? v2)) (into v1 v2)
    (coll? v1) (merge v1 v2)
    :else nil))

(defn join-output-resolver
  "Resolver that joins two strings in the :out key with newlines."
  [k v1 v2]
  (when (and (= :out k)
             (string? v1)
             (string? v2))
    (s/join "\n" [v1 v2])))

