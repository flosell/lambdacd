(ns lambdacd.core
  (:use compojure.core)
  (:require [lambdacd.server :as server]
            [clojure.core.async :as async]
            [lambdacd.execution :as execution]
            [lambdacd.pipeline-state :as pipeline-state]
            [lambdacd.new-ui :as new-ui]
            [compojure.route :as route]
            [ring.util.response :as resp]
            [lambdacd.manualtrigger :as manualtrigger]
            [lambdacd.util :as util]))


(defn- start-pipeline-thread [pipeline-def context]
  (async/thread (while true (execution/run pipeline-def context))))


(defn- mk-complete-route [pipline-def state]
  (routes
    (context "/old" [] (server/ui-for pipline-def state))
    (context "/ui2" [] (new-ui/new-ui-routes pipline-def state))
    (GET "/" [] (resp/redirect "old/"))
    (route/resources "/")
    (route/not-found "<h1>Page not found</h1>")))


(defn mk-pipeline [pipeline-def config]
  (let [state (atom (pipeline-state/initial-pipeline-state config))
        context {:_pipeline-state state
                 :config config}]
    {:ring-handler (mk-complete-route pipeline-def state)
     :init (partial start-pipeline-thread pipeline-def context)}))