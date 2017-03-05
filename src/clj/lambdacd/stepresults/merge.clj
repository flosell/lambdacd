(ns lambdacd.stepresults.merge
  "Functions that can help merge several step results into one")

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
