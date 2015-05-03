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

(defn- is-scrolled-to-bottom []
  (let [delta (- (- js/document.body.scrollHeight js/document.body.scrollTop) js/document.body.clientHeight)
        is-bottom (< delta 20)]
    is-bottom))

(defn scroll-wrapper [component]
  (let [scrolled-to-bottom-before-update (atom false)
        before-update #(reset! scrolled-to-bottom-before-update (is-scrolled-to-bottom))
        after-update #(if @scrolled-to-bottom-before-update (scroll-to-bottom))
        wrapped-component (with-meta component
                             {:component-will-mount before-update
                              :component-will-update before-update
                              :component-did-update after-update
                              :component-did-mount after-update})]
    [wrapped-component]))


(defn- plain-output-component [build-state step-id-to-display details-visible]
  (let [step (state/find-by-step-id build-state step-id-to-display)
        result (:result step )
        output (:out result)]
    [:div {:class "results"}
     [:button {:on-click (negate details-visible)} (if @details-visible "-" "+")]
     [details-component details-visible result]
     [:pre output]]))


(defn output-component [build-state step-id-to-display details-visible]
  (scroll-wrapper (partial plain-output-component build-state step-id-to-display details-visible)))