(ns lambdacd.ui.ui-server
  (:require [compojure.route :as route]
            [lambdacd.ui.api :as api]
            [lambdacd.ui.ui-page :as ui-page]
            [compojure.core :refer [routes GET context]]))

(defn ui-for
  ([pipeline]
   (routes
     (context "/api" [] (api/rest-api pipeline))
     (route/resources "/" {:root "public"})
     (GET "/" [] (ui-page/ui-page pipeline)))))
