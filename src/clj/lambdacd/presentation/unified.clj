(ns lambdacd.presentation.unified
  (:require [lambdacd.presentation.pipeline-structure :as pipeline-structure]))

(defn- unify [step build-state]
  (let [step-id (:step-id step)
        result-for-step (get build-state step-id {})
        with-result (assoc step :result result-for-step )
        children (:children step)
        unified-children (map #(unify % build-state) children)
        with-children (assoc with-result :children unified-children)]
    with-children))

(defn unified-presentation [pipeline-def build-state]
  (let [structure (pipeline-structure/pipeline-display-representation pipeline-def)]
    (map #(unify % build-state) structure)))