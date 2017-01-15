(ns lambdacd.history
  (:require [reagent.core :as reagent :refer [atom]]
            [goog.events :as events]
            [re-frame.core :as re-frame]
            [lambdacd.db :as db]
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
  (case status
    "failure" "fa fa-times failure-red"
    "success" "fa fa-check success-green"
    "running" "fa fa-cog fa-spin running-blue"
    "waiting" "fa fa-pause waiting-yellow"
    "killed"  "fa fa-bug"
    "fa fa-question"))

(defn- icon [class]
  [:div {:class "history--item--status-icon history--item--line--item" } [:i {:class class}]])


(defn- has-metadata? [build-metadata]
  (and build-metadata
       (not= {} build-metadata)))

(defn- build-label [build-number {build-label :human-readable-build-label}]
  (if build-label
    (str build-label " (#" build-number ")")
    (str "Build " build-number)))

(defn history-item-component [active-build-number
                              {build-number          :build-number
                               status                :status
                               first-updated-at      :first-updated-at
                               retriggered           :retriggered
                               duration-in-seconds   :duration-in-sec
                               build-metadata        :build-metadata}]
  [:li {:key build-number :class (str "history--item" (if (= build-number active-build-number) " history--item--active"))}
   [:a {:href (route/for-build-number build-number) :class "history--item--container"}
    [:div {:class "history--item--line"}
     [icon (status-icon status)]
     [:h3 {:class "history--item--line--item" } (build-label build-number build-metadata)]]
    [:div {:class "history--item--line"}
     [icon "fa fa-play"]
     [:p {:class "history--item--line--item" } (if first-updated-at
                                                 (str "Started: " (time/format-ago first-updated-at))
                                                 "Not started yet")]]
    [:div {:class "history--item--line"}
     [icon "fa fa-clock-o"]
     [:p {:class "history--item--line--item" } (if-not (zero? duration-in-seconds)
                                                 (str "Duration: " (time/format-duration-long
                                                                     duration-in-seconds))
                                                 "Duration: 0sec")]]
    (if (has-metadata? build-metadata)
      [:div {:class "history--item--line tooltip"}
       [icon "fa fa-info-circle"]
       [:p {:class "history--item--line--item"} "Metadata"]
       [:span [:pre (utils/pretty-print-map build-metadata)]]])
    (if retriggered
      [:div {:class "history--item--line"}
       [icon "fa fa-repeat"]
       [:p {:class "history--item--line--item" } (str "Retriggered #" retriggered)]])]])

(defn build-history-renderer [history active-build-number]
  [:div {:id "builds" :class "app__history history l-horizontal"}
   [:h2 {:key "history-builds"} "Builds"]
   (if-not (nil? history)
     (let [history-to-display (sort-by :build-number > history)]
       [:ul {:key "history-items"} (map #(history-item-component active-build-number %) history-to-display)])
     (commons/loading-screen))])

(defn build-history-component []
  (let [active-build-number (re-frame/subscribe [::db/build-number])
        history             (re-frame/subscribe [::db/history])]
    (fn []
      (build-history-renderer @history @active-build-number))))
