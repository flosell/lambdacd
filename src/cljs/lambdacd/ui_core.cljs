(ns lambdacd.ui-core
  (:require-macros [cljs.core.async.macros :refer [go go-loop]])
  (:require [reagent.core :as reagent :refer [atom]]
            [goog.events :as events]
            [goog.history.EventType :as EventType]
            [cljs.core.async :as async]
            [lambdacd.utils :as utils]
            [lambdacd.api :as api]
            [lambdacd.pipeline :as pipeline]
            [lambdacd.route :as route]
            [lambdacd.state :as state])
  (:import goog.History))

(enable-console-print!)

(def poll-frequency 1000)

(defn poll [atom fn]
  (go-loop []
    (let [history (async/<! (fn))]
      (reset! atom history))
    (async/<! (utils/timeout poll-frequency))
    (recur)))

(defn poll-history [history-atom]
  (poll history-atom api/get-build-history))

(defn poll-state [state-atom build-number-atom]
  (poll state-atom #(api/get-build-state @build-number-atom)))

(defn history-item-component [{build-number :build-number status :status}]
  [:li {:key build-number} [:a {:href (route/for-build-number build-number)} (str "Build " build-number)]])

(defn build-history-component [history]
  (let [history-to-display (sort-by :build-number history)]
    [:div {:class "blocked"}
     [:h2 "Builds"]
     [:ul (map history-item-component history-to-display)]]))


(defn output-component [build-state-atom step-id-to-display-atom]
  (let [step (state/find-by-step-id @build-state-atom @step-id-to-display-atom)
        output (:out (:result step ))]
    [:pre output]))

(defn current-build-component [build-state-atom build-number step-id-to-display-atom]
  [:div {:key build-number :class "blocked"}
   [:h2 (str "Current Build " build-number)]
   [:div {:id "pipeline" }
    [:ol
     (map #(pipeline/build-step-component % build-number) @build-state-atom)]]
   [:h2 "Output"]
   [output-component build-state-atom step-id-to-display-atom]])


(defn root [build-number-atom step-id-to-display-atom history state]
  (let [build-number @build-number-atom]
    (if build-number
      (do
        [:div
         [:div {:id "builds"} [build-history-component @history]]
          [:div {:id "currentBuild"} [current-build-component state build-number step-id-to-display-atom]]])
      [:div {:id "loading"}
       :h1 "Loading..."]
    )))


(defn- navigate [build-number-atom step-id-to-display-atom token]
  (let [nav-result (route/dispatch-route build-number-atom step-id-to-display-atom token)]
    (if (not (= :ok (:routing nav-result)))
      (.setToken (History.) (:redirect-to nav-result))
      )))

(defn hook-browser-navigation! [build-number-atom step-id-to-display-atom]
  (doto (History.)
    (events/listen
      EventType/NAVIGATE
      (fn [event]
        (navigate build-number-atom step-id-to-display-atom (.-token event))))
    (.setEnabled true)))

(defn init! []
  (let [build-number-atom (atom nil)
        step-id-to-display-atom (atom nil)
        history-atom (atom [])
        state-atom (atom [])]
    (poll-history history-atom)
    (poll-state state-atom build-number-atom)
    (hook-browser-navigation! build-number-atom step-id-to-display-atom)
    ; #' is necessary so that fighweel can update: https://github.com/reagent-project/reagent/issues/94
    (reagent/render-component [#'root build-number-atom step-id-to-display-atom history-atom state-atom] (.getElementById js/document "app"))))


