(ns lambdacd.core
  (:use compojure.core)
  (:require [lambdacd.ui.ui-server :as server]
            [clojure.core.async :as async]
            [lambdacd.internal.execution :as execution]
            [lambdacd.internal.pipeline-state :as pipeline-state]
            [compojure.route :as route]
            [ring.util.response :as resp]))


(defn- start-one-run-after-another [pipeline-def context]
  (async/thread (while true (execution/run pipeline-def context))))

(defn- start-pipeline-thread-after-first-step-finished [pipeline-def context]
  (let [pipeline-run-fn (fn [] (async/thread (execution/run pipeline-def context)))]
    (pipeline-state/notify-when-most-recent-build-running context pipeline-run-fn)
    (pipeline-run-fn)))

(defn- start-pipeline-thread [pipeline-def context start-next-run-after-first-step-finished]
  (if start-next-run-after-first-step-finished
    (start-pipeline-thread-after-first-step-finished pipeline-def context)
    (start-one-run-after-another pipeline-def context)))


(defn- mk-complete-route [pipline-def state ctx]
  (routes
    (context "/old" [] (server/ui-for pipline-def state ctx))
    (GET "/" [] (resp/redirect "old/"))
    (route/resources "/")
    (route/not-found "<h1>Page not found</h1>")))


(defn mk-pipeline [pipeline-def config]
  (let [state (atom (pipeline-state/initial-pipeline-state config))
          start-next-run-after-first-step-finished (get config :dont-wait-for-completion false)
        context {:_pipeline-state state
                 :config config}]
    {:ring-handler (mk-complete-route pipeline-def state context)
     :init (partial start-pipeline-thread pipeline-def context start-next-run-after-first-step-finished)
     :state state}))
