(ns lambdacd.internal.pipeline-state-test
  (:use [lambdacd.testsupport.test-util])
  (:refer-clojure :exclude [alias update])
  (:require [clojure.test :refer :all]
            [lambdacd.internal.pipeline-state :refer :all]
            [lambdacd.testsupport.test-util :as tu]
            [lambdacd.testsupport.data :refer [some-ctx-with some-ctx]]
            [lambdacd.event-bus :as event-bus]))

(deftest pipeline-state-updater-test
  (testing "that we tap into the event bus update the pipeline state with its information"
    (let [updates        (atom [])
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
              [1 [1 2] {:status :running :foo :bar}]] @updates))))
  (testing "shutdown behavior"
    (testing "that the pipeline-state-updater can be stopped with a message on the event bus"
      (let [ctx                 (some-ctx)
            pipeline-state      (reify PipelineStateComponent
                                  (update [_ _ _ _]
                                    (throw (Exception. "no update expected"))))
            updater-finished-ch (start-pipeline-state-updater pipeline-state ctx)]
        (tu/call-with-timeout 1000 (stop-pipeline-state-updater ctx updater-finished-ch))
        (is (not= {:status :timeout} (tu/get-or-timeout updater-finished-ch :timeout 1000)))))
    (testing "that stopping is idempotent"
      (let [ctx                 (some-ctx)
            pipeline-state      (reify PipelineStateComponent
                                  (update [_ _ _ _]
                                    (throw (Exception. "no update expected"))))
            updater-finished-ch (start-pipeline-state-updater pipeline-state ctx)]
        (tu/call-with-timeout 1000 (stop-pipeline-state-updater ctx updater-finished-ch))
        (tu/call-with-timeout 1000 (stop-pipeline-state-updater ctx updater-finished-ch))
        (is (not= {:status :timeout} (tu/get-or-timeout updater-finished-ch :timeout 1000)))
        (tu/call-with-timeout 1000 (stop-pipeline-state-updater ctx updater-finished-ch))))))