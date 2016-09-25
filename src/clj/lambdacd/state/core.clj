(ns lambdacd.state.core
  "Facade for all functions related to dealing with LambdaCDs state. Wraps the related interfaces to simplify compatibility and API."
  (:require [lambdacd.state.protocols :as protocols]
            [lambdacd.internal.pipeline-state :as legacy-pipeline-state]
            [lambdacd.presentation.pipeline-structure :as pipeline-structure]))

(defn- all-build-numbers-from-legacy [component]
  (->> (legacy-pipeline-state/get-all component)
       (keys)
       (sort)))

(defn- get-build-from-legacy [component build-number]
  {:step-results (-> (legacy-pipeline-state/get-all component)
                     (get build-number))})

; -------------------------------------------------------------------------

(defn state-component [ctx]
  (:pipeline-state-component ctx))

; -------------------------------------------------------------------------

(defn consume-step-result-update
  "Update a step-result in the state"
  [ctx build-number step-id step-result]
  (let [component (state-component ctx)]
    (if (satisfies? protocols/StepResultUpdateConsumer component)
      (protocols/consume-step-result-update component build-number step-id step-result)
      (legacy-pipeline-state/update component build-number step-id step-result))))

(defn consume-pipeline-structure [ctx build-number pipeline-structure-representation]
  (let [component (state-component ctx)]
    (if (satisfies? protocols/PipelineStructureConsumer component)
      (protocols/consume-pipeline-structure component build-number pipeline-structure-representation))))

(defn next-build-number
  "Returns the build number for the next build"
  [ctx]
  (let [component (state-component ctx)]
    (if (satisfies? protocols/NextBuildNumberSource component)
      (protocols/next-build-number component)
      (legacy-pipeline-state/next-build-number component))))

(defn all-build-numbers
  "Returns all existing build numbers"
  [ctx]
  (let [component (state-component ctx)]
    (if (satisfies? protocols/QueryAllBuildNumbersSource component)
      (protocols/all-build-numbers component)
      (all-build-numbers-from-legacy component))))

(defn get-build
  "Returns all information for a given build as a map with :step-results and TODO more"
  [ctx build-number]
  (let [component (state-component ctx)]
    (if (satisfies? protocols/QueryBuildSource component)
      (protocols/get-build component build-number)
      (get-build-from-legacy component build-number))))

(defn get-step-results
  "Returns a map from step-ids to step-results"
  [ctx build-number]
  (:step-results (get-build ctx build-number)))

(defn get-step-result
  "Returns a map containing the result of one step"
  [ctx build-number step-id]
  (get (get-step-results ctx build-number)
       step-id))

(defn get-pipeline-structure
  "Returns a map describing the structure of the pipeline"
  [ctx build-number]
  (or (:pipeline-structure (get-build ctx build-number))
      (pipeline-structure/pipeline-display-representation (:pipeline-def ctx))))
