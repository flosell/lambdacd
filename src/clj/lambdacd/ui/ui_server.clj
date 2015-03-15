(ns lambdacd.ui.ui-server
  (:use compojure.core)
  (:require [clojure.walk :as w]
            [ring.middleware.json :as ring-json]
            [clojure.data.json :as json :only [write-str]]
            [lambdacd.presentation.pipeline-structure :as presentation]
            [lambdacd.steps.manualtrigger :as manualtrigger]
            [lambdacd.util :as util]
            [ring.util.response :as resp]
            [lambdacd.internal.execution :as execution]
            [clojure.core.async :as async]))

(defn- pipeline [pipeline-def]
  (presentation/display-representation pipeline-def))


(defn ui-for [pipeline-def pipeline-state ctx]
  (ring-json/wrap-json-params
    (routes
      (GET "/api/pipeline" [] (util/json (pipeline pipeline-def)))
      (GET "/api/pipeline-state" [] (util/json @pipeline-state))
      (POST "/api/builds/:buildnumber/:step-id/retrigger" [buildnumber step-id]
            (do
              (async/thread (execution/retrigger pipeline-def ctx (read-string buildnumber) [(read-string step-id)]))
              (util/ok)))
      (POST "/api/dynamic/:id" {{id :id } :params data :json-params} (do
                                                   (util/json (manualtrigger/post-id id (w/keywordize-keys data)))))
      (GET "/" [] (resp/resource-response "index.html" {:root "public/old"})))))
