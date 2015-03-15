(ns lambdacd.ui-core
  (:require [reagent.core :as reagent :refer [atom]]
            [reagent.session :as session]
            [secretary.core :as secretary :include-macros true]
            [goog.events :as events]
            [goog.history.EventType :as EventType]
            [cljsjs.react :as react]
            [clojure.string :as str])
  (:import goog.History))

(enable-console-print!)

(defn build-history []
  [:div
  [:h1 "Builds"]])

(defn init! []
  (reagent/render-component [build-history] (.getElementById js/document "buildtitle")))