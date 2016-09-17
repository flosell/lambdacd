(ns lambdacd.state.protocols
  "defines protocols that need to be implemented by a state component")

(defprotocol StepResultUpdateConsumer
  "components implementing this protocol can provide the state of a pipeline"
  (consume-step-result-update [self build-number step-id step-result]))

