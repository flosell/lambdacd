(ns lambdacd.internal.pipeline-state)


; TODO: move this protocol out of internal once the interface is more polished and stable
; in the meantime, you can use this protocol but keep in mind it's subject to change
(defprotocol PipelineStateComponent
  "components implementing this protocol can provide the state of a pipeline"
  (update            [self build-number step-id step-result])
  (get-all           [self]) ;; should in the future be replaced with more detailed accessor functions
  (get-internal-state [self]) ;; FIXME: temporary, hack until runners are rewritten to use step-results-channel
  (next-build-number [self]))
