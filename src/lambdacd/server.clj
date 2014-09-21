(ns lambdacd.server
  (:use compojure.core)
  (:require [compojure.route :as route]
            [clojure.data.json :as json :only [write-str]]
            [lambdacd.presentation :as presentation]
            [lambdacd.manualtrigger :as manualtrigger]
            [lambdacd.util :as util]
            [ring.util.response :as resp]))

(defn- pipeline [pipeline-def]
  (presentation/display-representation pipeline-def))


;; TODO: this shouldn't actually exist, we should preprocess this somewhere else
(defn- serialize-channel [k v]
  (if (util/is-channel? v)
    :waiting
    v))

(defn- json [data]
  { :headers { "Content-Type" "application/json"}
    :body (json/write-str data :value-fn serialize-channel)
    :status 200 })

(defn ui-for [pipeline-def pipeline-state] (routes
  (GET  "/api/pipeline" [] (json (pipeline pipeline-def)))
  (GET  "/api/pipeline-state" [] (json @pipeline-state))
  (POST "/api/dynamic/:id" [id] (json (manualtrigger/post-id id)))
  (GET "/" [] (resp/resource-response "index.html" {:root "public"}))
  (route/resources "/")
  (route/not-found "<h1>Page not found</h1>")))
