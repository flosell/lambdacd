(ns lambdacd.state.protocols
  "Defines protocols that need to be implemented by a state component")

(defprotocol StepResultUpdateConsumer
  "Components implementing this protocol can update the state of the pipeline"
  (consume-step-result-update [self build-number step-id step-result]
    "Tells the component to update the result of a particular step. Is called on every update so it needs to handle lots of requests"))

(defprotocol NextBuildNumberSource
  "Components implementing this protocol provide the LambdaCD execution engine with new build numbers"
  (next-build-number [self]
    "Returns the build number for the next build. Must be comparable and greater than all existing build numbers"))

(defprotocol QueryAllBuildNumbersSource
  "Components implementing this protocol can supply a list of all build numbers present in the datastore"
  (all-build-numbers [self]
    "Returns a sorted list of build numbers present in the datastore"))

(defprotocol QueryBuildSource
  "Components implementing this protocol can supply information on one build"
  (get-build [self build-number]
    "Returns build information as a map with :step-results, and TODO"))
