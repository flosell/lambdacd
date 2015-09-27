(ns lambdacd.ui-core
  (:require-macros [cljs.core.async.macros :refer [go go-loop]])
  (:require
    [reagent.core :as reagent :refer [atom]]
    [lambdacd.utils :refer [classes]]
    [lambdacd.pipeline :as pipeline]
    [lambdacd.route :as route]
    [lambdacd.history :as history]
    [lambdacd.commons :as commons]
    [re-frame.core :as re-frame]
    [lambdacd.output :as output]
    [lambdacd.logic :as logic]
    [lambdacd.db :as db]))

(defn current-build-header-component [build-number]
  [:h2 {:key "build-header"} (str "Current Build " build-number)])

(defn current-build-component [build-state-atom build-number pipeline-component output-component header-component]
  (if-not (nil? @build-state-atom)
    [:div {:id "currentBuild" :class "app__current-build l-horizontal"}
     [header-component build-number]
     [pipeline-component]
     [output-component]]
    [commons/loading-screen]))

(defn wired-current-build-component [build-state-atom build-number]
  (current-build-component build-state-atom build-number pipeline/pipeline-component output/output-component current-build-header-component))

(defn root [build-number-atom state connection-state history-component current-build-component]
  (let [build-number @build-number-atom
        container-classes (if (= @connection-state :lost)
                            ["app" "l-horizontal" "app--connection-lost"]
                            ["app" "l-horizontal"] )]
      [:div {:class (classes container-classes)}
       [:div {:class "l-vertical app__content"}
        [history-component]
        [current-build-component state build-number]]]))

(defn init! []
  (re-frame/dispatch-sync [::db/initialize-db])
  (let [state-atom (re-frame/subscribe [::db/pipeline-state])
        build-number-atom (re-frame/subscribe [::db/build-number])
        connection-state (re-frame/subscribe [::db/connection-state])]
    (logic/start-ticker)
    (route/hook-browser-navigation! state-atom)
    ; #' is necessary so that fighweel can update: https://github.com/reagent-project/reagent/issues/94
    (reagent/render-component [#'root build-number-atom state-atom connection-state history/build-history-component wired-current-build-component] (.getElementById js/document "app"))))


