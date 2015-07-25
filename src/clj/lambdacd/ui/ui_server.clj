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
            [lambdacd.internal.pipeline-state :as pipeline-state]
            [lambdacd.core :as core]
            [clojure.string :as string]))

(defn- pipeline [pipeline-def]
  (presentation/display-representation pipeline-def))

(defn- build-infos [pipeline-def pipeline-state buildnumber]
  (let [build-number-as-int (util/parse-int buildnumber)
        build-state (get pipeline-state build-number-as-int)]
    (util/json (unified/unified-presentation pipeline-def build-state))))

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
(defn- ui []
  (routes
    (route/resources "/" {:root "public"})
    (GET "/" [] (resp/resource-response "index.html" {:root "public"}))))

(defn ui-for
  ([pipeline]
   (routes
     (context "/api" [] (rest-api pipeline))
     (context "" [] (ui))))
  ([pipeline-def pipeline-state ctx] ; this is deprecated and will be removed in subsequent releases
   (ui-for {:pipeline-def pipeline-def
            :context ctx})))
