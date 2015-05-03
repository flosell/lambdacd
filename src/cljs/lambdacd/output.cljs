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

(defn- scroll-to-bottom []
  (js/window.scrollTo 0 js/document.body.scrollHeight ))

(defn sticky [component]
  (with-meta component
             {:component-did-mount
              (fn [this]
                (js/Stickyfill.add (reagent/dom-node this)))}))

(defn plain-tail-button [do-tail]
  [:button {:on-click (negate do-tail) :class "tail"} (if @do-tail "don't follow output" "follow output")])

(defn tail-button [do-tail]
  [(sticky (partial plain-tail-button do-tail))])

(defn output-component [build-state step-id-to-display details-visible do-tail]
  (let [step (state/find-by-step-id build-state step-id-to-display)
        result (:result step )
        output (:out result)]
    (if @do-tail
      (scroll-to-bottom))
    [:div {:class "results"}
     [:button {:on-click (negate details-visible) :class "expand-details"} (if @details-visible "-" "+")]
     [tail-button do-tail]
     [details-component details-visible result]
     [:pre output]]))
