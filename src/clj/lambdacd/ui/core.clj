(ns lambdacd.ui.core
  (:require [lambdacd.ui.ui-page :as ui-page]
            [compojure.route :as route]
            [lambdacd.ui.api :as api]
            [compojure.core :refer [routes GET context]]))

(defn ui-for
  "Returns a ring-handler offering the LambdaCD API including resources, HTML and API."
  ([pipeline]
   (routes
     (context "/api" [] (api/rest-api pipeline))
     (route/resources "/" {:root "public"})
     (GET "/" [] (ui-page/ui-page pipeline)))))
