(ns lambdacd.ui.api
  (:require [lambdacd.presentation.unified :as unified]
            [ring.util.response :as resp]
            [clojure.string :as string]
            [ring.middleware.json :as ring-json]
            [lambdacd.presentation.pipeline-state :as state-presentation]
            [lambdacd.execution.core :as execution]
            [lambdacd.steps.manualtrigger :as manualtrigger]
            [clojure.walk :as w]
            [compojure.core :refer [routes GET POST]]
            [lambdacd.state.core :as state]
            [lambdacd.util.internal.sugar :as sugar]
            [lambdacd.ui.internal.util :as ui-util]))

(defn- build-infos [ctx build-number-str]
  (let [build-number       (sugar/parse-int build-number-str)
        pipeline-structure (state/get-pipeline-structure ctx build-number)
        step-results       (state/get-step-results ctx build-number)]
    (if (and pipeline-structure step-results)
      (ui-util/json (unified/pipeline-structure-with-step-results pipeline-structure step-results))
      (resp/not-found (str "build " build-number-str " does not exist")))))

(defn- to-internal-step-id [dash-seperated-step-id]
  (map sugar/parse-int (string/split dash-seperated-step-id #"-")))

(defn rest-api [{pipeline-def :pipeline-def ctx :context}]
  (ring-json/wrap-json-params
    (routes
      (GET "/builds/" [] (ui-util/json (state-presentation/history-for ctx)))
      (GET "/builds/:buildnumber/" [buildnumber] (build-infos ctx buildnumber))
      (POST "/builds/:buildnumber/:step-id/retrigger" [buildnumber step-id]
        (let [new-buildnumber (execution/retrigger-pipeline-async pipeline-def ctx (sugar/parse-int buildnumber) (to-internal-step-id step-id))]
          (ui-util/json {:build-number new-buildnumber})))
      (POST "/builds/:buildnumber/:step-id/kill" [buildnumber step-id]
        (do
          (execution/kill-step ctx (sugar/parse-int buildnumber) (to-internal-step-id step-id))
          "OK"))
      (POST "/dynamic/:id" {{id :id} :params data :json-params} (do
                                                                  (manualtrigger/post-id ctx id (w/keywordize-keys data))
                                                                  (ui-util/json {:status :success}))))))
