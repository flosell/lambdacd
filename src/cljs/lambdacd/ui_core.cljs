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


(defn container-build-step-component [step-id status children name output-atom ul-or-ol on-click-fn retrigger-elem build-number]
  [:li {:key step-id :data-status status :on-click on-click-fn}
   [:span {:class "build-step"} name ]
   retrigger-elem
   [ul-or-ol (map #(build-step-component  % output-atom build-number) children)]])

(defn click-handler [handler]
  (fn [evt]
    (handler)
    (.stopPropagation evt)))

(defn ask-for [parameters]
  (into {} (doall (map (fn [[param-name param-config]]
                [param-name (js/prompt (str "Please enter a value for " (name param-name) ": " (:desc param-config)))]) parameters))))


(defn manual-trigger [{ trigger-id :trigger-id parameters :parameters}]
  (if parameters
    (api/trigger trigger-id (ask-for parameters))
    (api/trigger trigger-id {})))

(defn is-finished [step]
  (let [status (:status (:result step))
        is-finished (or (= "success" status) (= "failure" status) (= "killed" status))]
    is-finished))

(defn can-be-retriggered? [step]
  (let [step-id (:step-id step)
        is-not-nested (= (count step-id) 1) ;; this is an implementation detail, retriggering of nested steps not properly implemented yet
        is-finished (is-finished step)]
    (and is-finished is-not-nested)))

(defn retrigger [build-number build-step]
  (api/retrigger build-number (first (:step-id build-step))))

(defn retrigger-component [build-number build-step]
  (if (can-be-retriggered? build-step)
    [:i {:class "fa fa-repeat retrigger" :on-click (click-handler #(retrigger build-number build-step))}]))

(defn manualtrigger-component [build-step]
  (let [result (:result build-step)
        trigger-id (:trigger-id result)]
    (if (and trigger-id (not (is-finished build-step)))
      [:i {:class "fa fa-play trigger" :on-click (click-handler #(manual-trigger result))}])))

(defn build-step-component [build-step output-atom build-number]
  (let [result (:result build-step)
        step-id (str (:step-id build-step))
        status (:status result)
        name (:name build-step)
        children (:children build-step)
        trigger-id (:trigger-id result)
        display-output (click-handler #(reset! output-atom (:out result)))
        retrigger-elem (retrigger-component build-number build-step)]
    (case (:type build-step)
      "parallel"  (container-build-step-component step-id status children name output-atom :ul display-output retrigger-elem build-number)
      "container" (container-build-step-component step-id status children name output-atom :ol display-output retrigger-elem build-number)
       [:li { :key step-id :data-status status :on-click display-output }
        [:span {:class "build-step"} name]
        (manualtrigger-component build-step)
        (retrigger-component build-number build-step)])))

(defn output-component [output-atom]
  [:pre @output-atom])

(defn current-build-component [build-state-atom build-number output-atom]
  [:div {:key build-number :class "blocked"}
   [:h2 (str "Current Build " build-number)]
   [:div {:id "pipeline" }
    [:ol
     (map #(build-step-component % output-atom build-number) @build-state-atom)]]
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
  (reagent/render-component [#'root] (.getElementById js/document "app")))


