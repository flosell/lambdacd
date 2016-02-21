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
            [hiccup.core :as h]
            [hiccup.page :as p]
            [lambdacd.internal.pipeline-state :as pipeline-state]
            [lambdacd.core :as core]
            [clojure.string :as string]
            [compojure.route :as route]
            [lambdacd.ui.api :as api]
            [lambdacd.ui.ui-page :as ui-page]))

(defn ui [pipeline]
  (let [pipeline-name (get-in pipeline [:context :config :name])]
    (routes
      (route/resources "/" {:root "public"})
      (GET "/" [] (ui-page/ui-page pipeline-name)))))

(defn ui-for
  ([pipeline]
   (routes
     (context "/api" [] (api/rest-api pipeline))
     (context "" [] (ui pipeline)))))
