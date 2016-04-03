(ns lambdacd.core
  (:use compojure.core)
  (:require [lambdacd.internal.default-pipeline-state :as default-pipeline-state]
            [lambdacd.internal.execution :as execution]
            [lambdacd.event-bus :as event-bus]
            [lambdacd.internal.running-builds-tracking :as running-builds-tracking]
            [lambdacd.internal.pipeline-state :as pipeline-state]))

(def default-config
  {:ms-to-wait-for-shutdown (* 10 1000)})

(defn assemble-pipeline
  ([pipeline-def config]
   (assemble-pipeline pipeline-def config (default-pipeline-state/new-default-pipeline-state config)))
  ([pipeline-def config pipeline-state-component]
   (let [context                (-> {:config        (merge default-config config)}
                                    (event-bus/initialize-event-bus)
                                    (running-builds-tracking/initialize-running-builds-tracking)
                                    (assoc :pipeline-state-component pipeline-state-component))
         pipeline-state-updater (pipeline-state/start-pipeline-state-updater (:pipeline-state-component context) context)]

     {:context                context
      :pipeline-def           pipeline-def
      :pipeline-state-updater pipeline-state-updater})))

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
