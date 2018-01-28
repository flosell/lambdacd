(ns lambdacd.core
  "Main entrypoinint into LambdaCD. Provides core functionality to assemble an instance of LambdaCD that can run."
  (:use compojure.core)
  (:require [lambdacd.internal.default-pipeline-state :as default-pipeline-state]
            [lambdacd.event-bus :as event-bus]
            [lambdacd.internal.running-builds-tracking :as running-builds-tracking]
            [lambdacd.state.internal.pipeline-state-updater :as pipeline-state-updater]
            [clojure.tools.logging :as log]
            [lambdacd.runners :as runners]
            [lambdacd.execution.internal.kill :as kill]
            [lambdacd.execution.core :as execution-core]))

(defn- add-shutdown-sequence! [ctx]
  (doto (Runtime/getRuntime)
    (.addShutdownHook (Thread. ^Runnable (fn []
                                 (log/info "Shutting down LambdaCD...")
                                 ((:shutdown-sequence (:config ctx)) ctx)))))
  ctx)

(defn default-shutdown-sequence
  "Default behavior when shutting down a Pipeline. Kills runnning pipelines, stops runners and internal processes."
  [ctx]
  (runners/stop-runner ctx)
  (kill/kill-all-pipelines ctx)
  (pipeline-state-updater/stop-pipeline-state-updater ctx))

(def ^{:doc "Default configuration if none other is specified"} default-config
  {:ms-to-wait-for-shutdown (* 10 1000)
   :shutdown-sequence default-shutdown-sequence
   :step-updates-per-sec 10
   :use-new-event-bus false})

(defn- initialize-pipeline-state-updater [ctx]
  (let [updater (pipeline-state-updater/start-pipeline-state-updater ctx)]
    (assoc ctx :pipeline-state-updater updater)))

(defn assemble-pipeline
  "Assemble various internal LambdaCD components into a unit ready to run.

  Returns a map that contains `:context` and `:pipeline-def`"
  ([pipeline-def config]
   (assemble-pipeline pipeline-def config (default-pipeline-state/new-default-pipeline-state config)))
  ([pipeline-def config pipeline-state-component]
   (let [context                (-> {:config (merge default-config config)}
                                    (event-bus/initialize-event-bus)
                                    (running-builds-tracking/initialize-running-builds-tracking)
                                    (assoc :pipeline-state-component pipeline-state-component)
                                    (assoc :pipeline-def pipeline-def)
                                    (initialize-pipeline-state-updater)
                                    (add-shutdown-sequence!))]
     {:context                context
      :pipeline-def           pipeline-def})))
