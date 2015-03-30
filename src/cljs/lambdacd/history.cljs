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

(defn history-item-component [{build-number :build-number status :status}]
  [:li {:key build-number} [:a {:href (route/for-build-number build-number)} (str "Build " build-number)]])

(defn build-history-component [history]
  [:div {:class "blocked"}
   [:h2 "Builds"]
   (if (not (nil? history))
     (let [history-to-display (sort-by :build-number history)]
       [:ul (map history-item-component history-to-display)])
     [commons/loading-screen])])
