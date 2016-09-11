(ns lambdacd.execution
  "Public interface to interact with the execution-engine"
  (:require [lambdacd.internal.execution :as internal]))

(defn retrigger [pipeline context build-number step-id-to-retrigger]
  (internal/retrigger-async pipeline context build-number step-id-to-retrigger))

(defn kill-step [ctx build-number step-id]
  (internal/kill-step ctx build-number step-id))

(defn execute-steps [steps args ctx & opts]
  (apply internal/execute-steps steps args ctx opts))

(defn execute-step
  ([args ctx step]
   (internal/execute-step args [ctx step]))
  ([args [ctx step]]
   (internal/execute-step args [ctx step])))

(defn run [pipeline context]
  (internal/run pipeline context))
