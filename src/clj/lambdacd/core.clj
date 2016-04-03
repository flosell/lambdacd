(ns lambdacd.core
  (:use compojure.core)
  (:require [lambdacd.internal.default-pipeline-state :as default-pipeline-state]
            [lambdacd.internal.execution :as execution]
            [lambdacd.event-bus :as event-bus]
            [lambdacd.internal.running-builds-tracking :as running-builds-tracking]
            [lambdacd.internal.pipeline-state :as pipeline-state]
            [clojure.tools.logging :as log]
            [lambdacd.runners :as runners]))

(defn- add-shutdown-sequence! [ctx]
  (doto (Runtime/getRuntime)
    (.addShutdownHook (Thread. (fn []
                                 (log/info "Shutting down LambdaCD...")
                                 ((:shutdown-sequence (:config ctx)) ctx)))))
  ctx)

(def default-shutdown-sequence
  (fn [ctx]
    (runners/stop-runner ctx)
    (execution/kill-all-pipelines ctx)
    (pipeline-state/stop-pipeline-state-updater ctx)))

(def default-config
  {:ms-to-wait-for-shutdown (* 10 1000)
   :shutdown-sequence default-shutdown-sequence})

(defn- initialize-pipeline-state-updater [ctx]
  (let [updater (pipeline-state/start-pipeline-state-updater (:pipeline-state-component ctx) ctx)]
    (assoc ctx :pipeline-state-updater updater)))

(defn assemble-pipeline
  ([pipeline-def config]
   (assemble-pipeline pipeline-def config (default-pipeline-state/new-default-pipeline-state config)))
  ([pipeline-def config pipeline-state-component]
   (let [context                (-> {:config (merge default-config config)}
                                    (event-bus/initialize-event-bus)
                                    (running-builds-tracking/initialize-running-builds-tracking)
                                    (assoc :pipeline-state-component pipeline-state-component)
                                    (initialize-pipeline-state-updater)
                                    (add-shutdown-sequence!))]
     {:context                context
      :pipeline-def           pipeline-def})))

(defn retrigger [pipeline context build-number step-id-to-retrigger]
  (execution/retrigger-async pipeline context build-number step-id-to-retrigger))

(defn kill-step [ctx build-number step-id]
  (execution/kill-step ctx build-number step-id))

(defn execute-steps [steps args ctx & opts]
  (apply execution/execute-steps steps args ctx opts))

(defn execute-step
  ([args ctx step]
   (execution/execute-step args [ctx step]))
  ([args [ctx step]]
   (execution/execute-step args [ctx step])))
