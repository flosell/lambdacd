(ns lambdacd.state.core
  "Facade for all functions related to dealing with LambdaCDs state. Wraps the related interfaces to simplify compatibility and API."
  (:require [lambdacd.state.protocols :as protocols]
            [lambdacd.internal.pipeline-state :as legacy-pipeline-state]))

(defn state-component [ctx]
  (:pipeline-state-component ctx))

(defn consume-step-result-update
  "Update a step-result in the state"
  [ctx build-number step-id step-result]
  (let [component (state-component ctx)]
    (if (satisfies? lambdacd.state.protocols/StepResultUpdateConsumer component)
      (protocols/consume-step-result-update component build-number step-id step-result)
      (legacy-pipeline-state/update component build-number step-id step-result))))

(defn next-build-number
  "Returns the build number for the next build"
  [ctx]
  (let [component (state-component ctx)]
    (if (satisfies? lambdacd.state.protocols/BuildNumberSource component)
      (protocols/next-build-number component)
      (legacy-pipeline-state/next-build-number component))))
