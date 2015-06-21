(ns lambdacd.internal.pipeline-state)

(defprotocol PipelineStateComponent
  "components implementing this protocol can provide the state of a pipeline"
  (update  [self build-number step-id step-result])
  (get-all [self] ;; should in the future be replaced with more detailed accessor functions
    ))
