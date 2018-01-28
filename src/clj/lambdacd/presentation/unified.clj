(ns lambdacd.presentation.unified
  "This namespace contains functions to convert the current internal state of a pipeline and it's structure
  into a form that's easier to handle. Unifies the information from `lambdacd.presentation.pipeline-state` and `lambdacd.presentation.pipeline-structure`.
  Used for example to be able to display a pipeline and it's current state in a UI.")

(defn- unify-step [step build-state]
  (let [step-id          (:step-id step)
        result-for-step  (get build-state step-id {})
        with-result      (assoc step :result result-for-step)
        children         (:children step)
        unified-children (map #(unify-step % build-state) children)
        with-children    (assoc with-result :children unified-children)]
    with-children))

(defn pipeline-structure-with-step-results
  "Merges a pipeline structure with the step results for steps in the structure.

  Example:
  ```clojure
  > (let [pipeline-structure (state/get-pipeline-structure ctx build-number)
          step-results       (state/get-step-results ctx build-number)]
      (unified/pipeline-structure-with-step-results pipeline-structure step-results))
  [{:name             \"in-parallel\"
    :type             :parallel
    :step-id          '(1)
    :has-dependencies false
    :result           {:status :running}
    :children
                      [{:name             \"do-stuff\"
                        :type             :step
                        :step-id          '(1 1)
                        :has-dependencies false
                        :children         []
                        :result           {:status :failure
                                           :out    \"do stuff failed\"}}]}
                       {:name             \"do-other-stuff\"
                         :type             :step
                         :step-id          '(2 1)
                         :has-dependencies false
                         :children         []
                         :result           {:status :running
                                            :some-key :some-value}}]}]}]

  ```"
  [pipeline-structure step-results]
  (map #(unify-step % step-results) pipeline-structure))

