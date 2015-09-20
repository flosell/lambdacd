(ns lambdacd.ui-core
  (:require-macros [cljs.core.async.macros :refer [go go-loop]])
  (:require
    [reagent.core :as reagent :refer [atom]]
    [cljs.core.async :as async]
    [lambdacd.utils :as utils :refer [classes]]
    [lambdacd.api :as api]
    [lambdacd.pipeline :as pipeline]
    [lambdacd.route :as route]
    [lambdacd.history :as history]
    [lambdacd.commons :as commons]
    [re-frame.core :as re-frame]
    [lambdacd.output :as output]
    ; require those to make sure everything is initialized before we get going
    [lambdacd.db :as db]))

(enable-console-print!)

(defn noop [& _])

(def poll-frequency 1000)

(defn poll-and-dispatch [kw fn callback]
  (go-loop []
    (let [new-val (async/<! (fn))]
      (if new-val
        (do
          (re-frame/dispatch [kw new-val])
          (callback new-val))
        (re-frame/dispatch [::db/connection-lost]))
      )
    (async/<! (utils/timeout poll-frequency))
    (recur)))

(defn- most-recent-build-number [state]
  (->> state
       (map :build-number)
       (sort)
       (last)))

(defn- set-build-number-if-missing [build-number-atom]
  (fn [state]
    (if (nil? @build-number-atom)
      (route/set-build-number (most-recent-build-number state)))))

(defn poll-history [build-number-atom]
  (poll-and-dispatch ::db/history-updated api/get-build-history (set-build-number-if-missing build-number-atom)))

(defn poll-state [build-number-atom]
  (poll-and-dispatch ::db/pipeline-state-updated #(api/get-build-state @build-number-atom) noop))

(defn current-build-header-component [build-number]
  [:h2 {:key "build-header"} (str "Current Build " build-number)])

(defn current-build-component [build-state-atom build-number step-id-to-display-atom output-details-visible pipeline-component output-component header-component]
  (if-not (nil? @build-state-atom)
    (list
     [header-component build-number]
     [pipeline-component build-number build-state-atom @step-id-to-display-atom]
     [output-component @build-state-atom @step-id-to-display-atom output-details-visible])
    [commons/loading-screen]))

(defn wired-current-build-component [build-state-atom build-number step-id-to-display-atom output-details-visible]
  (current-build-component build-state-atom build-number step-id-to-display-atom output-details-visible pipeline/pipeline-component output/output-component current-build-header-component))

(defn header []
  [:div
   [:h1 "LambdaCD"]])

(defn root [build-number-atom step-id-to-display-atom history state output-details-visible connection-state history-component current-build-component header-component]
  (let [build-number @build-number-atom
        container-classes (if (= @connection-state :lost)
                            ["app" "l-horizontal" "app--connection-lost"]
                            ["app" "l-horizontal"] )]
      [:div {:class (classes container-classes)}
       [:div {:class "l-vertical app__content"}
         [:div {:id "builds" :class "app__history history l-horizontal"} (history-component @history build-number)]
         [:div {:id "currentBuild" :class "app__current-build l-horizontal"} (current-build-component state build-number step-id-to-display-atom output-details-visible)]]]))


(defn init! []
  (re-frame/dispatch-sync [::db/initialize-db])
  (let [history-atom (re-frame/subscribe [::db/history])
        state-atom (re-frame/subscribe [::db/pipeline-state])
        build-number-atom (re-frame/subscribe [::db/build-number])
        step-id-to-display-atom (atom nil)
        output-details-visible (atom false)
        connection-state (re-frame/subscribe [::db/connection-state])]
    (poll-history build-number-atom)
    (poll-state build-number-atom)
    (route/hook-browser-navigation! step-id-to-display-atom state-atom)
    ; #' is necessary so that fighweel can update: https://github.com/reagent-project/reagent/issues/94
    (reagent/render-component [#'root build-number-atom step-id-to-display-atom history-atom state-atom output-details-visible connection-state history/build-history-component wired-current-build-component header] (.getElementById js/document "app"))))


