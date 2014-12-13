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
  <link rel=\"stylesheet\" type=\"text/css\" href=\"/ui2/Semantic-UI-1.1.2/dist/semantic.css\"/>
<link rel=\"stylesheet\" type=\"text/css\" href=\"/ui2/css/main.css\"/>
<body>")

(def page-end "<script src=\"//ajax.googleapis.com/ajax/libs/jquery/2.1.1/jquery.min.js\"></script>
<script src=\"/ui2/Semantic-UI-1.1.2/dist/semantic.js\"></script>
</body>
</html>")

(def fake-history
  [{ :build-number 5
     :status :running}
   { :build-number 6
    :status :ok}
   { :build-number 7
    :status :failure}])

(def fake-pipeline-representation
  [{:name "wait-for-frontend-git" :type :step}
   {:name "in-parallel"
   :type :parallel
   :children
         [{:name "with-frontend-git"
           :type :container
           :children [{:name "client-package" :type :step}]}
          {:name "with-backend-git"
           :type :container
           :children [{:name "server-test" :type :step}
                      {:name "server-package" :type :step}]}]}
   {:name "in-cwd"
    :type :container
    :children [{:name "client-deploy" :type :step}
               {:name "server-deploy" :type :step}]}])

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

(defn- pipeline-step [{name :name type :type children :children}]
  (let [child-pipeline (pipeline children (not= type :parallel))
        title-div [:div.title name]
        content (if (empty? children)
                  title-div
                  (list title-div child-pipeline))
        result [:div.step
                 [:div.content
                  content]]]
    result))

(defn- pipeline [p horizontal]
  [:div {:class (if horizontal "div ui steps" "ui steps vertical")}
   (map pipeline-step p)]
  )

(defn history [h]
  (list [:h1 "Build History"]
        [:ul {:class "ui relaxed divided items"} (map history-item h)]))

(defn body [history content]
  (let [header [:h1 {:class "segment" } "LambdaCD"]
        history-column [:div {:class "ui five wide column"} history]
        content-column [:div {:class "ui eleven wide column"} content]
        columns [:div {:class "ui segment stackable grid"} history-column content-column]
        body (hc/html (list header columns))]
    (str page-start page-end body page-end)))

(defn new-ui-routes [pipeline-def pipeline-state]
  (routes
    (GET "/" [] (body (history (pipeline-state/history-for @pipeline-state)) (pipeline fake-pipeline-representation true)))))