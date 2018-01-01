(ns lambdacd.testsupport.noop-pipeline-state
  (:require [lambdacd.state.protocols :as protocols]))

(defrecord NoOpPipelineState []
  protocols/StepResultUpdateConsumer
  (consume-step-result-update [self build-number step-id step-result])
  protocols/PipelineStructureConsumer
  (consume-pipeline-structure [self build-number pipeline-structure-representation])
  protocols/NextBuildNumberSource
  (next-build-number [self] (throw (IllegalStateException. "not supported by NoOpPipelineState")))
  protocols/QueryAllBuildNumbersSource
  (all-build-numbers [self] (throw (IllegalStateException. "not supported by NoOpPipelineState")))
  protocols/QueryStepResultsSource
  (get-step-results [self build-number] (throw (IllegalStateException. "not supported by NoOpPipelineState")))
  protocols/PipelineStructureSource
  (get-pipeline-structure [self build-number] (throw (IllegalStateException. "not supported by NoOpPipelineState"))))

(defn new-no-op-pipeline-state []
  (->NoOpPipelineState))
