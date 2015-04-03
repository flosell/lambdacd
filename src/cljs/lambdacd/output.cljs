(ns lambdacd.output
  (:require [lambdacd.state :as state]
            [reagent.core :as reagent :refer [atom]]))

(defn negate [a]
  (fn [& _ ]
    (swap! a not)
    nil))

(defn- details-component [details-visible result]
  (if @details-visible
    [:pre (js/JSON.stringify (clj->js result) nil 2)]
    [:pre]))

(defn output-component [build-state step-id-to-display details-visible]
  (let [step (state/find-by-step-id build-state step-id-to-display)
        result (:result step )
        output (:out result)]
    [:div {:class "results"}
     [:button {:on-click (negate details-visible)} (if @details-visible "-" "+")]
     [details-component details-visible result]
     [:pre output]]))