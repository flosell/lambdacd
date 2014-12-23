(ns lambdacd.new-ui
  (:use compojure.core)
  (:require [compojure.route :as route]
            [hiccup.core :as hc]
            [clojure.data.json :as json :only [write-str]]
            [lambdacd.presentation :as presentation]
            [lambdacd.manualtrigger :as manualtrigger]
            [lambdacd.util :as util]
            [ring.util.response :as resp]
            [lambdacd.execution :as execution]
            [lambdacd.pipeline-state :as pipeline-state]))
;; FIXME: proper hiccup here:
(def page-start "<html>
  <head>
  <title>LambdaCD - Pipeline</title>
  </head>
  <link rel=\"stylesheet\" type=\"text/css\" href=\"/ui2/semantic-ui/semantic.css\"/>
<link rel=\"stylesheet\" type=\"text/css\" href=\"/ui2/css/main.css\"/>
<body>")

(def page-end "<script src=\"//ajax.googleapis.com/ajax/libs/jquery/2.1.1/jquery.min.js\"></script>
<script src=\"/ui2/semantic-ui/semantic.js\"></script>
</body>
</html>")

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

(defn- pipeline-step [build-state parent-step-id index {name :name type :type children :children}]
  (let [step-id (conj parent-step-id (inc index))
        step-state (get build-state step-id)
        step-status (:status step-state)
        child-pipeline (pipeline children (not= type :parallel) build-state step-id)
        title-div [:div.title name]
        content (if (empty? children)
                  title-div
                  (list title-div child-pipeline))
        result [:div.step {:data-step-id (str step-id) :data-status step-status}
                 [:div.content
                  content]]]
    ;(println "bs" build-state "sid" step-id "stepstate" step-state)
    result))

(defn- pipeline [p horizontal build-state parent-step-id]
  [:div {:class (if horizontal "div ui steps" "ui steps vertical")}
   (map-indexed (partial pipeline-step build-state parent-step-id) p)])

(defn history [h]
  (list [:h1 "Build History"]
        [:ul {:class "ui relaxed divided items"} (map history-item h)]))

(defn body [history content]
  (let [header [:h1 {:class "segment" } "LambdaCD"]
        history-column [:div {:class "ui three wide column"} history]
        content-column [:div {:class "ui thirteen wide column"} content]
        columns [:div {:class "ui segment stackable grid"} history-column content-column]
        body (hc/html (list header columns))]
    (str page-start page-end body page-end)))

(defn- pipeline-view [pipeline-def pipeline-state build-number]
  (let [build-history (pipeline-state/history-for pipeline-state)
        pipeline-state-for-build (get pipeline-state (Integer/parseInt build-number))]
    (println "build number" build-number)
    (body (history build-history)
          (pipeline (presentation/display-representation pipeline-def) true pipeline-state-for-build (list)))))

(defn new-ui-routes [pipeline-def pipeline-state]
  (routes
    (GET "/:build-number" [build-number] (pipeline-view pipeline-def @pipeline-state build-number))
    (GET "/" [] (resp/redirect (str "./" (pipeline-state/most-recent-build-number-in @pipeline-state))))))