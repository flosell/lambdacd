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
            [lambdacd.state :as state]
            [goog.string :as gstring]
            [clojure.string :as s]))

(defn- status-icon [status]
  (let [class (case status
                "failure" "fa fa-times"
                "success" "fa fa-check"
                "running" "fa fa-cog fa-spin"
                "waiting" "fa fa-pause"
                "killed"  "fa fa-bug"
                "fa fa-question")]
    [:div {:class "history--item--status-icon" } [:i {:class class}]]))

(defn- content-or-nbsp [c]
  (if (s/blank? c)
    (gstring/unescapeEntities "&nbsp;")
    c))

(defn history-item-component [build-number-to-display
                              {build-number          :build-number
                               status                :status
                               most-recent-update-at :most-recent-update-at
                               first-updated-at      :first-updated-at}]
  [:li {:key build-number :class (str "history--item" (if (= build-number build-number-to-display) " history--item--active"))}
   [:a {:href (route/for-build-number build-number) :class "history--item--container"}
     [status-icon status]
     [:div {:class "history-item-content"}
       [:span { :class "history-item-header"} (str "Build " build-number)]
       [:p {:class "history--item--detail-line"} status]
       [:i {:class "history--item--detail-line"} (content-or-nbsp
                                                   (time/format-duration first-updated-at most-recent-update-at))]
      ]]])

(defn build-history-component [history build-number]
  [:div {:class "history"}
   [:h2 "Builds"]
   (if-not (nil? history)
     (let [history-to-display (sort-by :build-number > history)]
       [:ul (map #(history-item-component build-number %) history-to-display)])
     [commons/loading-screen])])
