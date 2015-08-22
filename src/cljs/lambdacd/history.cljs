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
            [lambdacd.time :as time]
            [lambdacd.state :as state]))

(defn- status-icon [status]
  (let [class (case status
                "failure" "fa fa-times"
                "success" "fa fa-check"
                "running" "fa fa-cog fa-spin"
                "waiting" "fa fa-pause"
                "killed"  "fa fa-bug"
                "fa fa-question")]
    [:div {:class "history--item--status-icon" } [:i {:class class}]]))

(defn history-item-component [{build-number :build-number
                               status :status
                               most-recent-update-at :most-recent-update-at
                               first-updated-at :first-updated-at}]
  [:li {:key build-number :class "history--item"}
   [status-icon status]
   [:div {:class "history-item-content"}
     [:a {:href (route/for-build-number build-number) :class "history-item-header"} (str "Build " build-number)]
     [:p {:class "history--item--detail-line"} status]
     [:i {:class "history--item--detail-line"} (time/format-duration first-updated-at most-recent-update-at)]
    ]
   ])

(defn build-history-component [history]
  [:div {:class "history"}
   [:h2 "Builds"]
   (if-not (nil? history)
     (let [history-to-display (sort-by :build-number > history)]
       [:ul (map history-item-component history-to-display)])
     [commons/loading-screen])])
