(ns lambdacd.internal.pipeline-state-test
  (:use [lambdacd.testsupport.test-util])
  (:require [clojure.test :refer :all]
            [lambdacd.internal.pipeline-state :refer :all]
            [lambdacd.testsupport.test-util :as tu]
            [lambdacd.testsupport.data :refer [some-ctx-with some-ctx]]
            [lambdacd.util :as util]
            [lambdacd.event-bus :as event-bus]
            [clojure.core.async :as async]))

(deftest pipeline-state-updater-test
  (testing "that we tap into the event bus update the pipeline state with its information"
    (let [updates (atom [])
          ctx            (some-ctx)
          pipeline-state (reify PipelineStateComponent
                           (update [self build-number step-id step-result]
                             (swap! updates #(conj %1 [build-number step-id step-result]))))]
      (start-pipeline-state-updater pipeline-state ctx)

      (event-bus/publish ctx :step-result-updated {:build-number 1 :step-id [1 2] :step-result {:status :running}})
      (event-bus/publish ctx :step-result-updated {:build-number 2 :step-id [1 2] :step-result {:status :success}})
      (event-bus/publish ctx :step-result-updated {:build-number 1 :step-id [1 2] :step-result {:status :running :foo :bar}})

      (wait-for (= 3 (count @updates)))
      (is (= [[1 [1 2] {:status :running}]
              [2 [1 2] {:status :success}]
              [1 [1 2] {:status :running :foo :bar}]] @updates)))))