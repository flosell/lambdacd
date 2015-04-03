(ns lambdacd.output
  (:require [lambdacd.state :as state]))

(defn output-component [build-state step-id-to-display]
  (let [step (state/find-by-step-id build-state step-id-to-display)
        output (:out (:result step ))]
    [:pre output]))