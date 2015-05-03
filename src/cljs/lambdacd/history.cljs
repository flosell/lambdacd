(ns lambdacd.history
  (:require [reagent.core :as reagent :refer [atom]]
            [goog.events :as events]
            [goog.history.EventType :as EventType]
            [cljs.core.async :as async]
            [lambdacd.utils :as utils]
            [lambdacd.api :as api]
            [lambdacd.pipeline :as pipeline]
            [lambdacd.commons :as commons]
            [lambdacd.route :as route]
            [lambdacd.state :as state]))

(defn- status-icon [status]
  (let [class (case status
                "failure" "fa fa-times"
                "success" "fa fa-check"
                "running" "fa fa-cog fa-spin"
                "waiting" "fa fa-pause"
                "fa fa-question")]
    [:div {:class "history-item-status-icon" } [:i {:class class}]]))

(defn history-item-component [{build-number :build-number status :status}]
  [:li {:key build-number :class "history-item"}
   [status-icon status]
   [:div {:class "history-item-content"}
     [:a {:href (route/for-build-number build-number) :class "history-item-header"} (str "Build " build-number)]
     [:p {:class "history-item-detail"} status]]
   ])

(defn build-history-component [history]
  [:div {:class "blocked"}
   [:h2 "Builds"]
   (if (not (nil? history))
     (let [history-to-display (sort-by :build-number history)]
       [:ul (map history-item-component history-to-display)])
     [commons/loading-screen])])
