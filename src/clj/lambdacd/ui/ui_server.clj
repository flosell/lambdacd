(ns lambdacd.ui.ui-server
  (:use compojure.core)
  (:require [clojure.walk :as w]
            [ring.middleware.json :as ring-json]
            [clojure.data.json :as json :only [write-str]]
            [lambdacd.presentation.pipeline-structure :as presentation]
            [lambdacd.presentation.pipeline-state :as state-presentation]
            [lambdacd.steps.manualtrigger :as manualtrigger]
            [lambdacd.util :as util]
            [ring.util.response :as resp]
            [lambdacd.presentation.unified :as unified]
            [clojure.core.async :as async]
            [lambdacd.util :as util]
            [compojure.route :as route]
            [hiccup.core :as h]
            [hiccup.page :as p]
            [lambdacd.internal.pipeline-state :as pipeline-state]
            [lambdacd.core :as core]
            [clojure.string :as string]))

(defn- pipeline [pipeline-def]
  (presentation/pipeline-display-representation pipeline-def))

(defn- build-infos [pipeline-def pipeline-state buildnumber]
  (let [build-number-as-int (util/parse-int buildnumber)
        build-state (get pipeline-state build-number-as-int)]
    (if build-state
      (util/json (unified/unified-presentation pipeline-def build-state))
      (resp/not-found (str "build " buildnumber " does not exist")))))

(defn- to-internal-step-id [dash-seperated-step-id]
  (map util/parse-int (string/split dash-seperated-step-id #"-")))

(defn- rest-api [{pipeline-def :pipeline-def ctx :context}]
  (let [pipeline-state-component (:pipeline-state-component ctx)]
    (ring-json/wrap-json-params
      (routes
        (GET "/builds/" [] (util/json (state-presentation/history-for (pipeline-state/get-all pipeline-state-component))))
        (GET "/builds/:buildnumber/" [buildnumber] (build-infos pipeline-def (pipeline-state/get-all pipeline-state-component) buildnumber))
        (POST "/builds/:buildnumber/:step-id/retrigger" [buildnumber step-id]
              (let [new-buildnumber (core/retrigger pipeline-def ctx (util/parse-int buildnumber) (to-internal-step-id step-id))]
                (util/json {:build-number new-buildnumber})))
        (POST "/builds/:buildnumber/:step-id/kill" [buildnumber step-id]
              (do
                (core/kill-step ctx (util/parse-int buildnumber) (to-internal-step-id step-id))
                "OK"))
        (POST "/dynamic/:id" {{id :id } :params data :json-params} (do
                                                                     (manualtrigger/post-id ctx id (w/keywordize-keys data))
                                                                     (util/json {:status :success})))))))
(defn- ui [pipeline]
  (let [pipeline-name (get-in pipeline [:context :config :name])]
    (routes
      (route/resources "/" {:root "public"})
      (GET "/" [] (h/html
                    [:html
                      [:head
                       (if pipeline-name
                         [:title (str pipeline-name " - LambdaCD")]
                         [:title "LambdaCD"])
                       [:link {:rel "icon" :type "image/png" :sizes "32x32" :href "favicon-32x32.png"}]
                       [:link {:rel "icon" :type "image/png" :sizes "96x96" :href "favicon-96x96.png"}]
                       [:link {:rel "icon" :type "image/png" :sizes "16x16" :href "favicon-16x16.png"}]
                       (p/include-css "css/thirdparty/normalize.css")
                       (p/include-css "css/main.css")
                       (p/include-css "css/thirdparty/font-awesome-4.4.0/css/font-awesome.min.css")]
                      [:body
                        [:div {:class "app l-horizontal" }
                         [:div {:class "app__header"}
                          [:a {:href "/"}
                            [:h1 {:class "app__header__lambdacd"} "LambdaCD"]]
                          (if pipeline-name
                            [:span {:class "app__header__pipeline-name" } pipeline-name])]
                         [:div {:id "app" }]]
                       (p/include-js "js-gen/app.js")]])))))

(defn ui-for
  ([pipeline]
   (routes
     (context "/api" [] (rest-api pipeline))
     (context "" [] (ui pipeline))))
  ([pipeline-def pipeline-state ctx] ; this is deprecated and will be removed in subsequent releases
   (ui-for {:pipeline-def pipeline-def
            :context ctx})))
