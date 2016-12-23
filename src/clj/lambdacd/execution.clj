(ns lambdacd.execution
  "Public interface to interact with the execution-engine"
  (:require [lambdacd.internal.execution :as internal]
            [lambdacd.execution.internal.execute-step :as execute-step]
            [lambdacd.execution.internal.execute-steps :as execute-steps]))

; TODO: we can probably deprecate this in favor of a package like lambdacd.execution.core for more consistency (like lambdacd.state.core)

(defn retrigger [pipeline context build-number step-id-to-retrigger]
  (internal/retrigger-async pipeline context build-number step-id-to-retrigger))

(defn kill-step [ctx build-number step-id]
  (internal/kill-step ctx build-number step-id))

(defn execute-steps [steps args ctx & opts]
  (apply execute-steps/execute-steps steps args ctx opts))

(defn execute-step
  ([args ctx step]
   (execute-step/execute-step args [ctx step]))
  ([args [ctx step]]
   (execute-step/execute-step args [ctx step])))

(defn run [pipeline context]
  (internal/run pipeline context))
