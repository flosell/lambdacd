(ns lambdacd.testcases
  (:require [reagent.core :as reagent :refer [atom]]
            [lambdacd.ui-core :as ui-core]))

(defn fake-history-component [& _]
  [:div "here is history"])

(defn fake-current-build-component [& _]
  [:div "here is current build"])

(defn render [component]
  (reagent/render-component component (.getElementById js/document "app")))

(defn main []
  (let [build-number           (atom 0)
        step-id                (atom [42])
        history                (atom [])
        state                  (atom {})
        output-details-visible (atom false)
        connection-lost        (atom false)]
    (render [#'ui-core/root build-number
                            step-id
                            history
                            state
                            output-details-visible
                            connection-lost

                            fake-history-component
                            fake-current-build-component])))

