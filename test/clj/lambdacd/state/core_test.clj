(ns lambdacd.state.core-test
  (:require [clojure.test :refer :all]
            [shrubbery.core :refer [mock received?]]
            [lambdacd.state.core :as s]
            [lambdacd.testsupport.data :refer [some-ctx-with]]
            [lambdacd.state.protocols :as state-protocols]
            [lambdacd.internal.pipeline-state :as legacy-pipeline-state]))

(def some-build-number 42)
(def some-step-id [0])
(def some-step-result {:foo :bat})

(deftest consume-step-result-update-test
  (testing "that calls to a StepResultUpdateConsumer will just pass through"
    (let [component (mock state-protocols/StepResultUpdateConsumer)]
      (s/consume-step-result-update (some-ctx-with :pipeline-state-component component)
                                    some-build-number some-step-id some-step-result)
    (is (received? component state-protocols/consume-step-result-update [some-build-number some-step-id some-step-result]))))
  (testing "that calls to a legacy PipelineStateComponent be mapped to that method"
    (let [component (mock legacy-pipeline-state/PipelineStateComponent)]
      (s/consume-step-result-update (some-ctx-with :pipeline-state-component component)
                                    some-build-number some-step-id some-step-result)
    (is (received? component legacy-pipeline-state/update [some-build-number some-step-id some-step-result])))))

(deftest next-build-number-test
  (testing "that calls to a BuildNumberSource will just pass through"
    (let [component (mock state-protocols/BuildNumberSource)]
      (s/next-build-number (some-ctx-with :pipeline-state-component component))
      (is (received? component state-protocols/next-build-number []))))
  (testing "that calls to a legacy PipelineStateComponent be mapped to that method"
    (let [component (mock legacy-pipeline-state/PipelineStateComponent)]
      (s/next-build-number (some-ctx-with :pipeline-state-component component))
      (is (received? component legacy-pipeline-state/next-build-number [])))))
