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
  [:h1 "Builds"])

(defn root []
  [:div [build-history]])

(defn init! []
  ; #' is necessary so that fighweel can update: https://github.com/reagent-project/reagent/issues/94
  (reagent/render-component [#'build-history] (.getElementById js/document "buildtitle")))