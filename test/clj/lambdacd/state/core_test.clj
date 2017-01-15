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
(def some-structure {:some :structure})
(def some-metadata {:some :metadata})

(defn ^{:display-type :container} foo [& _])
(defn bar [& _])
(def some-pipeline-def `((foo
                           bar)))

(def some-pipeline-def-structure [{:name                        "foo"
                                   :type                        :container
                                   :has-dependencies            false
                                   :pipeline-structure-fallback true
                                   :step-id                     `(1)
                                   :children                    [{:name                        "bar"
                                                                  :type                        :step
                                                                  :has-dependencies            false
                                                                  :pipeline-structure-fallback true
                                                                  :step-id                     `(1 1)}]}])

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

(deftest consume-pipeline-structure-test
  (testing "that calls to a PipelineStructureConsumer will just pass through"
    (let [component (mock state-protocols/PipelineStructureConsumer)]
      (s/consume-pipeline-structure (some-ctx-with :pipeline-state-component component)
                                    some-build-number some-structure)
      (is (received? component state-protocols/consume-pipeline-structure [some-build-number some-structure]))))
  (testing "that calls to a legacy PipelineStateComponent are ignored"
    (let [component (mock legacy-pipeline-state/PipelineStateComponent)]
      (s/consume-pipeline-structure (some-ctx-with :pipeline-state-component component)
                                    some-build-number some-structure))))

(deftest consume-build-metadata-test
  (testing "that calls to a BuildMetadataConsumer will just pass through"
    (let [component (mock state-protocols/BuildMetadataConsumer)]
      (s/consume-build-metadata (some-ctx-with :pipeline-state-component component)
                                some-build-number some-metadata)
      (is (received? component state-protocols/consume-build-metadata [some-build-number some-metadata]))))
  (testing "that calls to a legacy PipelineStateComponent are ignored"
    (let [component (mock legacy-pipeline-state/PipelineStateComponent)]
      (s/consume-build-metadata (some-ctx-with :pipeline-state-component component)
                                some-build-number some-metadata))))

(deftest next-build-number-test
  (testing "that calls to a BuildNumberSource will just pass through"
    (let [component (mock state-protocols/NextBuildNumberSource)]
      (s/next-build-number (some-ctx-with :pipeline-state-component component))
      (is (received? component state-protocols/next-build-number []))))
  (testing "that calls to a legacy PipelineStateComponent be mapped to that method"
    (let [component (mock legacy-pipeline-state/PipelineStateComponent)]
      (s/next-build-number (some-ctx-with :pipeline-state-component component))
      (is (received? component legacy-pipeline-state/next-build-number [])))))

(deftest all-build-numbers-test
  (testing "that calls to QueryAllBuildNumbersSource will just pass through"
    (let [component (mock state-protocols/QueryAllBuildNumbersSource)]
      (s/all-build-numbers (some-ctx-with :pipeline-state-component component))
      (is (received? component state-protocols/all-build-numbers []))))
  (testing "compatibility with PipelineStateComponent"
    (testing "an empty state"
      (let [component (mock legacy-pipeline-state/PipelineStateComponent {:get-all {}})]
        (is (= [] (s/all-build-numbers (some-ctx-with :pipeline-state-component component))))))
    (testing "state with builds returns sorted list"
      (let [component (mock legacy-pipeline-state/PipelineStateComponent {:get-all {5 {}
                                                                                    1 {}
                                                                                    2 {}}})]
        (is (= [1 2 5] (s/all-build-numbers (some-ctx-with :pipeline-state-component component))))))))

(deftest get-step-results-test
  (testing "that calls to QueryStepResultSource will just pass through"
    (let [component (mock state-protocols/QueryStepResultsSource {:get-step-results {:some :step-results}})]
      (is (= {:some :step-results} (s/get-step-results (some-ctx-with :pipeline-state-component component) 1)))
      (is (received? component state-protocols/get-step-results [1]))))
  (testing "compatibility with PipelineStateComponent"
    (testing "an empty state"
      (let [component (mock legacy-pipeline-state/PipelineStateComponent {:get-all {}})]
        (is (= nil (s/get-step-results (some-ctx-with :pipeline-state-component component) 1)))))
    (testing "state with builds returns sorted list"
      (let [component (mock legacy-pipeline-state/PipelineStateComponent {:get-all {1 {:some :build}}})]
        (is (= {:some :build} (s/get-step-results (some-ctx-with :pipeline-state-component component) 1)))))))

(deftest get-step-result-test
  (testing "that we can get a simple step-result"
    (with-redefs [s/get-step-results (constantly {[2 1] {:step :result}})]
      (is (= {:step :result} (s/get-step-result nil 1 [2 1]))))))

(deftest get-pipeline-structure-test
  (testing "that calls to PipelineStructureSource will just pass through"
    (let [component (mock state-protocols/PipelineStructureSource {:get-pipeline-structure {:some :pipeline-structure}})]
      (is (= {:some :pipeline-structure} (s/get-pipeline-structure (some-ctx-with :pipeline-state-component component) 1)))
      (is (received? component state-protocols/get-pipeline-structure [1]))))
  (testing "that we get the current pipeline structure if the component doesn't support PipelineStructures"
    (let [component (mock state-protocols/QueryStepResultsSource {:get-step-results {:some :step-results}})]
      (is (= some-pipeline-def-structure (s/get-pipeline-structure (some-ctx-with :pipeline-state-component component
                                                                                  :pipeline-def some-pipeline-def) 1)))))
  (testing "that we get the current pipeline structure if the component returns :fallback and annotate the structure accordingly"
    (let [component (mock state-protocols/PipelineStructureSource {:get-pipeline-structure :fallback})]
      (is (= some-pipeline-def-structure (s/get-pipeline-structure (some-ctx-with :pipeline-state-component component
                                                                                  :pipeline-def some-pipeline-def) 1))))))

(deftest get-pipeline-structure-test
  (testing "that calls to BuildMetadataSource will just pass through"
    (let [component (mock state-protocols/BuildMetadataSource {:get-build-metadata {:some :metadata}})]
      (is (= {:some :metadata} (s/get-build-metadata (some-ctx-with :pipeline-state-component component) 1)))
      (is (received? component state-protocols/get-build-metadata [1]))))
  (testing "that we get an empty map if BuildMetadata is not supported"
    (let [component (mock legacy-pipeline-state/PipelineStateComponent)]
      (is (= {} (s/get-build-metadata (some-ctx-with :pipeline-state-component component) 1)))))
  (testing "that we get an empty map if the component returns :fallback"
    (let [component (mock state-protocols/PipelineStructureSource {:get-build-metadata :fallback})]
      (is (= {} (s/get-build-metadata (some-ctx-with :pipeline-state-component component) 1))))))
