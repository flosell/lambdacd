(ns lambdacd.ui-core
  (:require-macros [cljs.core.async.macros :refer [go go-loop]])
  (:require [reagent.core :as reagent :refer [atom]]
            [cljs.core.async :as async]
            [lambdacd.utils :as utils]
            [lambdacd.api :as api]
            )
  (:import goog.History))

(enable-console-print!)

(def history-poll-frequency 5000)


(defn poll [atom fn]
  (go-loop []
    (let [history (async/<! (fn))]
      (reset! atom history))
    (async/<! (utils/timeout history-poll-frequency))
    (recur)))

(defn poll-history [history-atom]
  (poll history-atom api/get-build-history))

(defn poll-state [history-atom build-number-atom]
  (poll history-atom #(api/get-build-state @build-number-atom)))

(defn history-item-component [{build-number :build-number status :status}]
  [:li {:key build-number} [:a {:href (str "?build=" build-number)} (str "Build " build-number)]])

(defn build-history-component [history]
  (let [history-to-display (sort-by :build-number @history)]
    [:div {:class "blocked"}
     [:h2 "Builds"]
     [:ul (map history-item-component history-to-display)]]))

(defn build-step-component [build-step output-atom]
  (let [step-id (str (:step-id build-step))
        status (:status (:result build-step))]
    (case (:type build-step)
      "parallel"  [:li {:key step-id :data-status status } [:ul (map #(build-step-component  % output-atom)(:children build-step))]]
      "container" [:li {:key step-id :data-status status} [:ol (map #(build-step-component % output-atom) (:children build-step))]]
       [:li { :key step-id :data-status status :on-click #(reset! output-atom (:out (:result build-step)))}
             [:span (:name build-step)]
             [:i {:class "fa fa-play trigger"}]
             [:i {:class "fa fa-repeat retrigger"}]])))

(defn output-component [output-atom]
  [:pre @output-atom])

(defn current-build-component [build-state-atom build-number output-atom]
  [:div {:key build-number :class "blocked"}
   [:h2 (str "Current Build " @build-number)]
   [:div {:id "pipeline" }
    [:ol
     (map #(build-step-component % output-atom) @build-state-atom)]]
   [:h2 "Output"]
   [output-component output-atom]])


(defn root []
  (let [history (atom [])
        state (atom [])
        build-number (atom 1)
        output-atom (atom "")]
    (poll-history history)
    (poll-state state build-number)
    [:div
     [:div {:id "builds"} [build-history-component history]]
      [:div {:id "currentBuild"} [current-build-component state build-number output-atom]]]))

(defn init! []
  ; #' is necessary so that fighweel can update: https://github.com/reagent-project/reagent/issues/94
  (reagent/render-component [#'root] (.getElementById js/document "dynamic")))


