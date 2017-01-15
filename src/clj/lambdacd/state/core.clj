(ns lambdacd.state.core
  "Facade for all functions related to dealing with LambdaCDs state. Wraps the related interfaces to simplify compatibility and API."
  (:require [lambdacd.state.protocols :as protocols]
            [lambdacd.internal.pipeline-state :as legacy-pipeline-state]
            [lambdacd.presentation.pipeline-structure :as pipeline-structure]))

(defn- all-build-numbers-from-legacy [component]
  (->> (legacy-pipeline-state/get-all component)
       (keys)
       (sort)))

(defn- get-step-results-from-legacy [component build-number]
  (get (legacy-pipeline-state/get-all component) build-number))

(defn- state-component [ctx]
  (:pipeline-state-component ctx))

(defn- annotated-step [step]
  (let [annotate-children (fn [x]
                            (if (:children x)
                              (assoc x :children (map annotated-step (:children x)))
                              x))]
    (-> step
        (assoc :pipeline-structure-fallback true)
        (annotate-children))))

(defn- annotated-fallback-structure [ctx]
  (let [current-structure (pipeline-structure/pipeline-display-representation (:pipeline-def ctx))]
    (map annotated-step current-structure)))

(defn- stored-structure-or-fallback [ctx build-number]
  (let [stored-structure (protocols/get-pipeline-structure (state-component ctx) build-number)]
    (if (= :fallback stored-structure)
      (annotated-fallback-structure ctx)
      stored-structure)))

(defn- stored-metadata-or-fallback [ctx build-number]
  (let [stored-metadata (protocols/get-build-metadata (state-component ctx) build-number)]
    (if (= :fallback stored-metadata)
      {}
      stored-metadata)))

; -------------------------------------------------------------------------

(defn consume-step-result-update
  "Update a step-result in the state"
  [ctx build-number step-id step-result]
  (let [component (state-component ctx)]
    (if (satisfies? protocols/StepResultUpdateConsumer component)
      (protocols/consume-step-result-update component build-number step-id step-result)
      (legacy-pipeline-state/update component build-number step-id step-result))))

(defn consume-pipeline-structure
  "Update the pipeline structure in the state"
  [ctx build-number pipeline-structure-representation]
  (let [component (state-component ctx)]
    (if (satisfies? protocols/PipelineStructureConsumer component)
      (protocols/consume-pipeline-structure component build-number pipeline-structure-representation))))

(defn consume-build-metadata
  "Update build metdata in the state"
  [ctx build-number metadata]
  (let [component (state-component ctx)]
    (if (satisfies? protocols/BuildMetadataConsumer component)
      (protocols/consume-build-metadata component build-number metadata))))

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

(defn get-step-results
  "Returns a map from step-ids to step-results"
  [ctx build-number]
  (let [component (state-component ctx)]
    (if (satisfies? protocols/QueryStepResultsSource component)
      (protocols/get-step-results component build-number)
      (get-step-results-from-legacy component build-number))))

(defn get-step-result
  "Returns a map containing the result of one step"
  [ctx build-number step-id]
  (get (get-step-results ctx build-number)
       step-id))

(defn get-pipeline-structure
  "Returns a map describing the structure of the pipeline"
  [ctx build-number]
  (let [component (state-component ctx)]
    (if (satisfies? protocols/PipelineStructureSource component)
      (stored-structure-or-fallback ctx build-number)
      (annotated-fallback-structure ctx))))

(defn get-build-metadata
  "Returns a map describing metadata of a build"
  [ctx build-number]
  (let [component (state-component ctx)]
    (if (satisfies? protocols/BuildMetadataSource component)
      (stored-metadata-or-fallback ctx build-number)
      {})))
