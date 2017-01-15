(ns lambdacd.execution.internal.pipeline-test
  (:require [clojure.test :refer :all]
            [lambdacd.execution.internal.pipeline :refer :all]
            [lambdacd.testsupport.data :refer [some-ctx some-ctx-with]]
            [shrubbery.core :refer [mock received?]]
            [lambdacd.state.protocols :as state-protocols]
            [lambdacd.util.internal.sugar :refer [not-nil?]]
            [lambdacd.presentation.pipeline-structure :as pipeline-structure])
  (:import (clojure.lang Atom)))

(defn step-pipeline
  "creates a pipeline structure with only the given step"
  [step]
  `(~step))

(defn some-step [args ctx]
  {:status :success
   :step   1})
(defn some-other-step [args ctx]
  {:status :success
   :step   2})

(defn some-step-expecting-a-build-number [args ctx]
  (assert (not-nil? (:build-number ctx)))
  {:status :success})

(defn some-step-expecting-build-metadata-atom [args ctx]
  (assert (instance? Atom  (:build-metadata-atom ctx)))
  {:status :success})

(def some-pipeline
  `(some-step
     some-other-step))

(def some-build-number 1)

(deftest run-pipeline-test
  (testing "that it executes the whole pipeline"
    (is (= {:status  :success
            :outputs {[1] {:status :success
                           :step   1}
                      [2] {:status :success
                           :step   2}}}
           (run-pipeline some-pipeline (some-ctx) some-build-number))))
  (testing "that it writes the pipeline structure into state"
    (let [state-component    (mock state-protocols/PipelineStructureConsumer
                                   state-protocols/StepResultUpdateConsumer)
          expected-structure (pipeline-structure/pipeline-display-representation some-pipeline)]
      (run-pipeline some-pipeline (some-ctx-with :pipeline-state-component state-component) some-build-number)

      (is (received? state-component state-protocols/consume-pipeline-structure [some-build-number expected-structure]))))
  (testing "that it passes the build-number on to steps"
    (is (= :success (:status (run-pipeline (step-pipeline `some-step-expecting-a-build-number) (some-ctx) some-build-number)))))
  (testing "that it passes a build-metadata atom to steps"
    (is (= :success (:status (run-pipeline (step-pipeline `some-step-expecting-build-metadata-atom) (some-ctx) some-build-number))))))
