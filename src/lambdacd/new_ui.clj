(ns lambdacd.new-ui
  (:use compojure.core)
  (:require [compojure.route :as route]
            [hiccup.core :as hc]
            [hiccup.page :as page]
            [clojure.data.json :as json :only [write-str]]
            [lambdacd.presentation.pipeline-structure :as presentation]
            [lambdacd.steps.manualtrigger :as manualtrigger]
            [lambdacd.util :as util]
            [ring.util.response :as resp]
            [lambdacd.execution :as execution]
            [lambdacd.pipeline-state :as pipeline-state]))

(defn- refresh-every [seconds]
  [:meta {:http-equiv "refresh" :content (str seconds)}])

(defn page [body]
  (page/html5
         [:head
          [:title "LambdaCD - Pipeline"]
          (refresh-every 5)
          (page/include-css "semantic-ui/semantic.css")
          (page/include-css "css/main.css")
          (page/include-js "//ajax.googleapis.com/ajax/libs/jquery/2.1.1/jquery.min.js")
          (page/include-js "semantic-ui/semantic.js")]
          [:body
           body]))

(defn- icon-style-for [{status :status}]
  (case status
    :running "teal notched circle loading icon"
    :ok "green check circle icon"
    :failure "red remove circle icon"
    :waiting "blue wait circle icon"
    :unknown "yellow help circle icon"))

(defn- status-label-for [{status :status}]
  (case status
    :running "running..."
    :ok "successful"
    :failure "failure"
    :waiting "waiting..."
    :unknown "unknown"))

(defn history-item [item]
  [:li {:class "item"}
   [:div {:class "ui mini image"}
    [:i {:class (icon-style-for item)}]]
   [:div {:class "content"}
    [:div {:class "header"} (str "Build " (:build-number item))]
    [:div {:class "meta"} (status-label-for item)]]]
  )

(declare pipeline) ;; mutual recursion...

(defn- pipeline-step [build-state {name :name type :type children :children step-id :step-id}]
  (let [
        step-state (get build-state step-id)
        step-status (:status step-state)
        child-pipeline (pipeline children (not= type :parallel) build-state)
        title-div [:div.title name]
        content (if (empty? children)
                  title-div
                  (list title-div child-pipeline))
        result [:div.step {:data-step-id (str step-id) :data-status step-status}
                 [:div.content
                  content]]]
    result))

(defn- pipeline [p horizontal build-state]
  [:div {:class (if horizontal "div ui steps" "ui steps vertical")}
   (map (partial pipeline-step build-state) p)])

(defn history [h]
  (list [:h1 "Build History"]
        [:ul {:class "ui relaxed divided items"} (map history-item h)]))

(defn body [history content]
  (let [header [:h1 {:class "segment" } "LambdaCD"]
        history-column [:div {:class "ui three wide column"} history]
        content-column [:div {:class "ui thirteen wide column"} content]
        columns [:div {:class "ui segment stackable grid"} history-column content-column]
        body (list header columns)]
    body))

(defn- pipeline-view [pipeline-def pipeline-state build-number]
  (let [build-history (pipeline-state/history-for pipeline-state)
        pipeline-state-for-build (get pipeline-state (Integer/parseInt build-number))]
    (println "build number" build-number)
    (hc/html (page (body (history build-history)
          (pipeline (presentation/display-representation pipeline-def) true pipeline-state-for-build))))))

(defn new-ui-routes [pipeline-def pipeline-state]
  (routes
    (GET "/:build-number" [build-number] (pipeline-view pipeline-def @pipeline-state build-number))
    (GET "/" [] (resp/redirect (str "./" (pipeline-state/most-recent-build-number-in @pipeline-state))))))
