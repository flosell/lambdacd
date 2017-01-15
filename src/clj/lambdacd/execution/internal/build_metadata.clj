(ns lambdacd.execution.internal.build-metadata
  (:require [lambdacd.state.core :as state]))

(defn- validate-metadata [x]
  (associative? x))

(defn add-metadata-atom [ctx]
  (let [metadata-atom (atom {} :validator validate-metadata)]
    (add-watch metadata-atom :update-state (fn [_ _ _ new]
                                             (state/consume-build-metadata ctx (:build-number ctx) new)))
    (assoc ctx :build-metadata-atom metadata-atom)))
