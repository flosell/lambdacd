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

(defn poll-history [history-atom]
  (go-loop []
    (let [history (async/<! (api/get-build-history))]
      (reset! history-atom history))
    (async/<! (utils/timeout history-poll-frequency))
    (recur)))

(defn history-item-component [{build-number :build-number status :status}]
  [:li {:key build-number} [:a {:href (str "?build=" build-number)} (str "Build " build-number)]])

(defn build-history-component [history]
  (let [history-to-display (sort-by :build-number @history)]
    [:div
     [:h2 "Builds"]
     [:ul (map history-item-component history-to-display)]]))

(defn root []
  (let [history (atom [{:build-number 2} {:build-number 1}])]
    (poll-history history)
    [:div [build-history-component history]]))

(defn init! []
  ; #' is necessary so that fighweel can update: https://github.com/reagent-project/reagent/issues/94
  (reagent/render-component [#'root] (.getElementById js/document "builds")))


