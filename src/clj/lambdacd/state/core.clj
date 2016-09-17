(ns lambdacd.state.core
  "Facade for all functions related to dealing with LambdaCDs state. Wraps the related interfaces to simplify compatibility and API."
  (:require [lambdacd.state.protocols :as protocols]
            [lambdacd.internal.pipeline-state :as legacy-pipeline-state]))

(defn state-component [ctx]
  (:pipeline-state-component ctx))

(defn consume-step-result-update
  "update a step-result in the state"
  [ctx build-number step-id step-result]
  (let [component (state-component ctx)]
    (if (satisfies? lambdacd.state.protocols/StepResultUpdateConsumer component)
      (protocols/consume-step-result-update component build-number step-id step-result)
      (legacy-pipeline-state/update component build-number step-id step-result))))
