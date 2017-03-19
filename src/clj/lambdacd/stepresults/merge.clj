(ns lambdacd.stepresults.merge
  "Functions that can help merge several step results into one"
  (:require [lambdacd.util :as utils]
            [lambdacd.stepresults.merge-resolvers :as merge-resolvers]
            [lambdacd.util.internal.map :as map-utils]))

(defn merge-step-results
  "Takes a list of step results (e.g. whats in the `:outputs` key of a nesting step-result)
  and merges it into one step result with the help of a function that can merge two step results:

  ```clojure
  > (merge-step-results [{:status :success}
                         {:foo :bar}
                         {:foo :baz}]
                        merge)
  {:status :success
   :foo    :baz}
  ```"
  [step-results merge-two-results-fn]
  (reduce merge-two-results-fn {} step-results))


(defn- resolve-first-matching [resolvers]
  (fn [k v1 v2]
    (->> resolvers
         (map #(% k v1 v2))
         (filter (complement nil?))
         (first))))

(defn merge-two-step-results
  "Takes two step results and merges them:

  ```clojure
  > (merge-two-step-results {:status :failure
                             :m      {:a :b}
                             :s      \"a\"}
                            {:status :success
                             :m      {:b :c}
                             :s      \"b\"})
  {:status :failure
   :m      {:a :b
            :b :c}
   :s      \"b\"}
  ```

  Optionally, `merge-two-step-results` takes a list of functions to customize how to resolve conflicts.
  Resolver-functions take the key where the conflict occurred and the two values and a merged result or nil if they can't merge the conflict.
  If one resolver can't resolve a conflict, the next one in the list is tried."
  [a b & {:keys [resolvers]
          :or   {resolvers [merge-resolvers/status-resolver
                            merge-resolvers/merge-nested-maps-resolver
                            merge-resolvers/combine-to-list-resolver
                            merge-resolvers/second-wins-resolver]}}]
  (map-utils/merge-with-k-v (resolve-first-matching resolvers) a b))
