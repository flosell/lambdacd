(ns lambdacd.execution.core
  "Entrypoint into the pipeline/step execution engine."
  (:require [clojure.core.async :as async]
            [lambdacd.state.core :as state]
            [lambdacd.execution.internal.pipeline :as pipeline]
            [lambdacd.execution.internal.execute-step :as execute-step]
            [lambdacd.execution.internal.execute-steps :as execute-steps]
            [lambdacd.execution.internal.kill :as kill]
            [lambdacd.execution.internal.retrigger :as retrigger]))

(defn run-pipeline
  "Execute a complete run of the pipeline.
  Returns with the full result of the pipeline execution:
  ```clojure
  > (run-pipeline pipeline ctx)
  {:status  :success
   :outputs {[1] {:status :success}
             [2] {:status :success
                  :outputs {[1 2} {:status :success}}
  ```"
  {:doc/format :markdown}
  [pipeline ctx]
  (pipeline/run-pipeline pipeline ctx (state/next-build-number ctx) {}))

(defn retrigger-pipeline
  "Retriggers a previous build of the pipeline, starting from a particular step-id.
  Returns the full results of the pipeline execution (see run-pipeline for details)"
  [pipeline context build-number step-id-to-run next-build-number]
  (let [new-ctx                (assoc context :retriggered-build-number build-number
                                              :retriggered-step-id step-id-to-run)
        initial-build-metadata (or
                                 (state/get-build-metadata new-ctx (:retriggered-build-number new-ctx))
                                 {})]
    (pipeline/run-pipeline pipeline new-ctx next-build-number initial-build-metadata)))

(defn retrigger-pipeline-async
  "Retriggers a previous build of the pipeline asynchronously and returning only the build number of the new pipeline-execution."
  [pipeline context build-number step-id-to-run]
  (let [next-build-number (state/next-build-number context)]
    (async/thread
      (retrigger-pipeline pipeline context build-number step-id-to-run next-build-number))
    next-build-number))

(defn execute-step
  "Execute a single step within a pipeline execution.
  Takes the arguments to pass to the step, the ctx and the step-function to call.
  Often used when implementing container steps (i.e. steps that call other, child steps)."
  ([args ctx step]
   (execute-step/execute-step args [ctx step]))
  ([args [ctx step]]
   (execute-step/execute-step args [ctx step])))

(defn execute-steps
  "Execute a number of steps in a particular way (configured by opts). Usually used when implementing container steps (i.e. steps that call other, child steps).

  Opts:

  * `:step-results-producer` A function that can execute a set of steps and return a step result in the end. Defaults to serial execution
  * `:is-killed` An atom with vaule true or false if the parent step needs control over when child steps are killed. Optional
  * `:unify-results-fn` A function that takes a collection of step results and returns a single step result that will be the result of the step while it is in progress. Used to control the parent
  * `:retrigger-predicate` A function that takes a steps context and the step itself and returns a keyword on what to do when retriggering: `:run` if the step should just run normally, `:rerun` if we rerun a step that ran before or `:mock` if we just mock the steps run by returning the previous result. Defaults to behavior that makes sense of steps run in sequence."
  {:doc/format :markdown}
  [steps args ctx & opts]
  (apply execute-steps/execute-steps steps args ctx opts))

(defn kill-step
  "Send an event to kill a particular step and return immediately."
  [ctx build-number step-id]
  (kill/kill-step ctx build-number step-id))
