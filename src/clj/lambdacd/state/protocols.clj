(ns lambdacd.state.protocols
  "Defines protocols that need to be implemented by a state component")

(defprotocol StepResultUpdateConsumer
  "Components implementing this protocol can update the state of the pipeline"
  (consume-step-result-update [self build-number step-id step-result]
    "Tells the component to update the result of a particular step. Is called on every update so it needs to handle lots of requests"))

(defprotocol PipelineStructureConsumer
  "Components implementing this protocol can set the structure a pipeline had for a particular build"
  (consume-pipeline-structure [self build-number pipeline-structure-representation]
    "Tells the component to update the structure of a particular build."))

(defprotocol BuildMetadataConsumer
  "Components implementing this protocol can update the metadata for a particular build"
  (consume-build-metadata [self build-number metadata]
    "Tells the component to update the metadata of a particular build."))

(defprotocol NextBuildNumberSource
  "Components implementing this protocol provide the LambdaCD execution engine with new build numbers"
  (next-build-number [self]
    "Returns the build number for the next build. Must be an integer and greater than all existing build numbers"))

(defprotocol QueryAllBuildNumbersSource
  "Components implementing this protocol can supply a list of all build numbers present in the datastore"
  (all-build-numbers [self]
    "Returns a sorted list of build numbers present in the datastore"))

(defprotocol QueryStepResultsSource
  "Components implementing this protocol can supply steps results of a build"
  (get-step-results [self build-number]
    "Returns a map of step-id to step results"))

(defprotocol PipelineStructureSource
  "Components implementing this protocol can supply the structure of the pipeline for a particular build"
  (get-pipeline-structure [self build-number]
    "Returns a map describing the pipeline of for a particular build"))

(defprotocol BuildMetadataSource
  "Components implementing this protocol can supply metadata for a particular build"
  (get-build-metadata [self build-number]
    "Returns a map describing the metadata for a particular build"))

