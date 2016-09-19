(ns lambdacd.ui.api
  (:require [lambdacd.util :as util]
            [lambdacd.presentation.unified :as unified]
            [ring.util.response :as resp]
            [clojure.string :as string]
            [ring.middleware.json :as ring-json]
            [lambdacd.presentation.pipeline-state :as state-presentation]
            [lambdacd.execution :as execution]
            [lambdacd.steps.manualtrigger :as manualtrigger]
            [clojure.walk :as w]
            [compojure.core :refer [routes GET POST]]
            [lambdacd.state.core :as state]))

(defn- build-infos [pipeline-def ctx buildnumber]
  (let [build-state (state/get-step-results ctx (util/parse-int buildnumber))]
    (if build-state
      (util/json (unified/unified-presentation pipeline-def build-state))
      (resp/not-found (str "build " buildnumber " does not exist")))))

(defn- to-internal-step-id [dash-seperated-step-id]
  (map util/parse-int (string/split dash-seperated-step-id #"-")))

(defn rest-api [{pipeline-def :pipeline-def ctx :context}]
  (ring-json/wrap-json-params
    (routes
      (GET "/builds/" [] (util/json (state-presentation/history-for ctx)))
      (GET "/builds/:buildnumber/" [buildnumber] (build-infos pipeline-def ctx buildnumber))
      (POST "/builds/:buildnumber/:step-id/retrigger" [buildnumber step-id]
        (let [new-buildnumber (execution/retrigger pipeline-def ctx (util/parse-int buildnumber) (to-internal-step-id step-id))]
          (util/json {:build-number new-buildnumber})))
      (POST "/builds/:buildnumber/:step-id/kill" [buildnumber step-id]
        (do
          (execution/kill-step ctx (util/parse-int buildnumber) (to-internal-step-id step-id))
          "OK"))
      (POST "/dynamic/:id" {{id :id} :params data :json-params} (do
                                                                  (manualtrigger/post-id ctx id (w/keywordize-keys data))
                                                                  (util/json {:status :success}))))))
