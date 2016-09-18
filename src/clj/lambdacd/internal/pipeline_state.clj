(ns lambdacd.internal.pipeline-state
  (:refer-clojure :exclude [update]))

(defprotocol PipelineStateComponent
  "DEPRECATED: USE lambdacd.state.protocols instead"
  (update            [self build-number step-id step-result])
  (get-all           [self])
  (get-internal-state [self])
  (next-build-number [self]))
