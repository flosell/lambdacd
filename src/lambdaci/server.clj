(ns lambdaci.server
  (:use compojure.core)
  (:require [compojure.route :as route]
            [todopipeline.pipeline :as todo]
            [clojure.data.json :as json :only [write-str]]
            [lambdaci.visual :as visual]))

(defn pipeline []
  (visual/display-representation todo/pipeline))

(defn json [data]
  { :headers { "Content-Type" "application/json"}
    :body (json/write-str data)
    :status 200 })

(defroutes app
  (GET "/api/pipeline" [] (json (pipeline)))
  (route/resources "/")
  (route/not-found "<h1>Page not found</h1>"))
