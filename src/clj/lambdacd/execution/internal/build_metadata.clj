(ns lambdacd.execution.internal.build-metadata
  (:require [lambdacd.state.core :as state]))

(defn- validate-metadata [x]
  (associative? x))

(defn add-metadata-atom [ctx initial-metadata]
  (let [metadata-atom (atom initial-metadata :validator validate-metadata)]
    (state/consume-build-metadata ctx (:build-number ctx) initial-metadata)
    (add-watch metadata-atom :update-state (fn [_ _ _ new]
                                             (state/consume-build-metadata ctx (:build-number ctx) new)))
    (assoc ctx :build-metadata-atom metadata-atom)))
