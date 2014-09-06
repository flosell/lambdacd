(ns lambdaci.server
  (:use compojure.core)
  (:require [compojure.route :as route]
            [todopipeline.pipeline :as todo]
            [clojure.data.json :as json :only [write-str]]
            [lambdaci.visual :as visual]
            [lambdaci.manualtrigger :as manualtrigger]
            [lambdaci.dsl :as dsl]
            [ring.util.response :as resp]
            [clojure.core.async :as async]))

(defn- pipeline []
  (visual/display-representation todo/pipeline))

(defn- run-pipeline []
  (dsl/run todo/pipeline))

(defn start-pipeline-thread []
  (async/thread (while true (run-pipeline))))

(defn- pipeline-state []
  (dsl/get-pipeline-state))

;; TODO: we shouldn't actually exists, we should preprocess this somewhere else
(defn- serialize-channel [k v]
  (if (dsl/is-channel? v)
    :waiting
    v))

(defn- json [data]
  { :headers { "Content-Type" "application/json"}
    :body (json/write-str data :value-fn serialize-channel)
    :status 200 })

(defroutes app
  (GET  "/api/pipeline" [] (json (pipeline)))
  (GET  "/api/pipeline-state" [] (json (pipeline-state)))
  (POST "/api/pipeline" [] (json (run-pipeline)))
  (GET  "/api/dynamic/:id" [id] (json (manualtrigger/was-posted? id)))
  (POST "/api/dynamic/:id" [id] (json (manualtrigger/post-id id)))
  (GET "/" [] (resp/resource-response "index.html" {:root "public"}))
  (route/resources "/")
  (route/not-found "<h1>Page not found</h1>"))

