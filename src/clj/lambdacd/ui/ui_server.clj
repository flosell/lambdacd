(ns lambdacd.ui.ui-server
  (:use compojure.core)
  (:require [compojure.route :as route]
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
