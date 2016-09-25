(ns lambdacd.presentation.unified
  (:require [lambdacd.presentation.pipeline-structure :as pipeline-structure]))

(defn- unify-step [step build-state]
  (let [step-id          (:step-id step)
        result-for-step  (get build-state step-id {})
        with-result      (assoc step :result result-for-step)
        children         (:children step)
        unified-children (map #(unify-step % build-state) children)
        with-children    (assoc with-result :children unified-children)]
    with-children))

(defn pipeline-structure-with-step-results [pipeline-structure step-results]
  (map #(unify-step % step-results) pipeline-structure))

(defn unified-presentation [pipeline-def step-results]
  (let [structure (pipeline-structure/pipeline-display-representation pipeline-def)]
    (pipeline-structure-with-step-results structure step-results)))
