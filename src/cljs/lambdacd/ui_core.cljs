(ns lambdacd.ui-core
  (:require-macros [cljs.core.async.macros :refer [go go-loop]])
  (:require [reagent.core :as reagent :refer [atom]]
            [cljs.core.async :as async]
            [lambdacd.utils :as utils]
            [lambdacd.api :as api]
            [lambdacd.pipeline :as pipeline]
            [lambdacd.route :as route]
            [lambdacd.history :as history]
            [lambdacd.commons :as commons]
            [lambdacd.output :as output]))

(enable-console-print!)

(def poll-frequency 1000)

(defn poll [atom connection-lost-atom fn]
  (go-loop []
    (let [new-val (async/<! (fn))]
      (if new-val
        (do
          (reset! connection-lost-atom false)
          (reset! atom new-val))
        (reset! connection-lost-atom true))
      )
    (async/<! (utils/timeout poll-frequency))
    (recur)))

(defn poll-history [history-atom connection-lost-atom]
  (poll history-atom connection-lost-atom api/get-build-history))

(defn poll-state [state-atom build-number-atom connection-lost-atom]
  (poll state-atom connection-lost-atom #(api/get-build-state @build-number-atom)))

(defn current-build-component [build-state-atom build-number step-id-to-display-atom output-details-visible]
  (if (not (nil? @build-state-atom))
    [:div {:key build-number :class "blocked"}
     [:h2 (str "Current Build " build-number)]
     [pipeline/pipeline-component build-number build-state-atom]
     [output/output-component @build-state-atom @step-id-to-display-atom output-details-visible]]
    [commons/loading-screen]))


(defn root [build-number-atom step-id-to-display-atom history state output-details-visible connection-lost history-component current-build-component]
  (let [build-number @build-number-atom]
    (if build-number
      (do
        [:div (if @connection-lost {:class "app--connection-lost"})
         [:div {:id "builds"} [history-component @history]]
          [:div {:id "currentBuild"} [current-build-component state build-number step-id-to-display-atom output-details-visible]]])
      [:div {:id "loading"}
       [:h1 "Loading..."]]
    )))


(defn init! []
  (let [build-number-atom (atom nil)
        step-id-to-display-atom (atom nil)
        history-atom (atom nil)
        state-atom (atom nil)
        output-details-visible (atom false)
        connection-lost-atom (atom false)]
    (poll-history history-atom connection-lost-atom)
    (poll-state state-atom build-number-atom connection-lost-atom)
    (route/hook-browser-navigation! build-number-atom step-id-to-display-atom state-atom)
    ; #' is necessary so that fighweel can update: https://github.com/reagent-project/reagent/issues/94
    (reagent/render-component [#'root build-number-atom step-id-to-display-atom history-atom state-atom output-details-visible connection-lost-atom history/build-history-component current-build-component] (.getElementById js/document "app"))))


