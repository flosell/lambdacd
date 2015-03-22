(ns lambdacd.ui-core
  (:require-macros [cljs.core.async.macros :refer [go go-loop]])
  (:require [reagent.core :as reagent :refer [atom]]
            [secretary.core :as secretary :refer-macros [defroute]]
            [goog.events :as events]
            [goog.history.EventType :as EventType]
            [reagent.session :as session]
            [cljs.core.async :as async]
            [lambdacd.utils :as utils]
            [lambdacd.api :as api]
            )
  (:import goog.History))


(secretary/set-config! :prefix "#")

(defroute build-route "/builds/:buildnumber" [buildnumber] (session/put! :build-number buildnumber))
(defroute default-route "/" []
          (.setToken (History.) "/builds/1"))


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

(defn poll-state [history-atom build-number]
  (poll history-atom #(api/get-build-state build-number)))

(defn history-item-component [{build-number :build-number status :status}]
  [:li {:key build-number} [:a {:href (build-route {:buildnumber build-number})} (str "Build " build-number)]])

(defn build-history-component [history]
  (let [history-to-display (sort-by :build-number @history)]
    [:div {:class "blocked"}
     [:h2 "Builds"]
     [:ul (map history-item-component history-to-display)]]))

(declare build-step-component)


(defn container-build-step-component [step-id status children name output-atom ul-or-ol on-click-fn]
  [:li {:key step-id :data-status status :on-click on-click-fn}
   [:span name]
   [ul-or-ol (map #(build-step-component  % output-atom) children)]])

(defn build-step-component [build-step output-atom]
  (let [step-id (str (:step-id build-step))
        status (:status (:result build-step))
        name (:name build-step)
        children (:children build-step)
        on-click-fn (fn [evt]
                      (.stopPropagation evt)
                      (reset! output-atom (:out (:result build-step))))]
    (case (:type build-step)
      "parallel"  (container-build-step-component step-id status children name output-atom :ul on-click-fn)
      "container" (container-build-step-component step-id status children name output-atom :ol on-click-fn)
       [:li { :key step-id :data-status status :on-click on-click-fn }
        [:span name]
        [:i {:class "fa fa-play trigger"}]
             [:i {:class "fa fa-repeat retrigger"}]])))

(defn output-component [output-atom]
  [:pre @output-atom])

(defn current-build-component [build-state-atom build-number output-atom]
  [:div {:key build-number :class "blocked"}
   [:h2 (str "Current Build " build-number)]
   [:div {:id "pipeline" }
    [:ol
     (map #(build-step-component % output-atom) @build-state-atom)]]
   [:h2 "Output"]
   [output-component output-atom]])


(defn root []
  (let [history (atom [])
        state (atom [])
        build-number (session/get :build-number)
        output-atom (atom "")]
    (if build-number
      (do
        (poll-history history)
        (poll-state state build-number)
        [:div
         [:div {:id "builds"} [build-history-component history]]
          [:div {:id "currentBuild"} [current-build-component state build-number output-atom]]])
      [:div {:id "loading"}
       :h1 "Loading..."]
    )))

;; -------------------------
;; History
;; must be called after routes have been defined
(defn hook-browser-navigation! []
  (doto (History.)
    (events/listen
      EventType/NAVIGATE
      (fn [event]
        (secretary/dispatch! (.-token event))))
    (.setEnabled true)))

(defn init! []
  (hook-browser-navigation!)
  ; #' is necessary so that fighweel can update: https://github.com/reagent-project/reagent/issues/94
  (reagent/render-component [#'root] (.getElementById js/document "dynamic")))


