(ns lambdacd.internal.pipeline-state)

(defprotocol PipelineStateComponent
  "components implementing this protocol can provide the state of a pipeline"
  (update-step-result [self build-number step-id step-result])
  (get-all            [self]) ;; should in the future be replaced with more detailed accessor functions
  (get-internal-state [self]) ;; FIXME: temporary, hack until runners are rewritten to use step-results-channel
  (next-build-number  [self]))
