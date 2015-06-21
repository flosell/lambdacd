(ns lambdacd.testsupport.noop-pipeline-state
  (:require [lambdacd.internal.pipeline-state :as pipeline-state]))

(defrecord NoOpPipelineState []
  pipeline-state/PipelineStateComponent
  (update            [self build-number step-id step-result])
  (get-all           [self] (throw (IllegalStateException. "not supported by NoOpPipelineState"))) ;; should in the future be replaced with more detailed accessor functions
  (next-build-number [self] (throw (IllegalStateException. "not supported by NoOpPipelineState"))))

(defn new-no-op-pipeline-state []
  (->NoOpPipelineState))