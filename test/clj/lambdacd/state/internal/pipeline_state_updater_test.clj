(ns lambdacd.state.internal.pipeline-state-updater-test
  (:use [lambdacd.testsupport.test-util])
  (:refer-clojure :exclude [alias update])
  (:require [clojure.test :refer :all]
            [lambdacd.state.internal.pipeline-state-updater :refer :all]
            [lambdacd.internal.pipeline-state :refer [PipelineStateComponent]]
            [lambdacd.testsupport.test-util :as tu]
            [lambdacd.testsupport.data :refer [some-ctx-with some-ctx]]
            [lambdacd.event-bus :as event-bus]))

(deftest pipeline-state-updater-test
         (testing "that we tap into the event bus update the pipeline state with its information"
                  (let [updates        (atom [])
                        pipeline-state (reify PipelineStateComponent
                                         (update [self build-number step-id step-result]
                                           (swap! updates #(conj %1 [build-number step-id step-result]))))
                        ctx            (some-ctx-with :pipeline-state-component pipeline-state)]

                    (event-bus/publish ctx :step-result-updated {:build-number 1 :step-id [1 2] :step-result {:status :running}})
                    (event-bus/publish ctx :step-result-updated {:build-number 2 :step-id [1 2] :step-result {:status :success}})
                    (event-bus/publish ctx :step-result-updated {:build-number 1 :step-id [1 2] :step-result {:status :running :foo :bar}})

                    (wait-for (= 3 (count @updates)))
                    (is (= [[1 [1 2] {:status :running}]
                            [2 [1 2] {:status :success}]
                            [1 [1 2] {:status :running :foo :bar}]] @updates))))
         (testing "shutdown behavior"
                  (testing "that the pipeline-state-updater can be stopped with a message on the event bus"
                           (let [pipeline-state      (reify PipelineStateComponent
                                                       (update [_ _ _ _]
                                                         (throw (Exception. "no update expected"))))
                                 ctx                 (some-ctx-with :pipeline-state-component pipeline-state)
                                 updater-finished-ch (start-pipeline-state-updater ctx)]
                             (tu/call-with-timeout 1000 (stop-pipeline-state-updater (assoc ctx :pipeline-state-updater updater-finished-ch)))
                             (is (not= {:status :timeout} (tu/get-or-timeout updater-finished-ch :timeout 1000)))))
                  (testing "that stopping is idempotent"
                           (let [pipeline-state      (reify PipelineStateComponent
                                                       (update [_ _ _ _]
                                                         (throw (Exception. "no update expected"))))
                                 ctx                 (some-ctx-with :pipeline-state-component pipeline-state)
                                 updater-finished-ch (start-pipeline-state-updater ctx)]
                             (tu/call-with-timeout 1000 (stop-pipeline-state-updater (assoc ctx :pipeline-state-updater updater-finished-ch)))
                             (tu/call-with-timeout 1000 (stop-pipeline-state-updater (assoc ctx :pipeline-state-updater updater-finished-ch)))
                             (is (not= {:status :timeout} (tu/get-or-timeout updater-finished-ch :timeout 1000)))
                             (tu/call-with-timeout 1000 (stop-pipeline-state-updater (assoc ctx :pipeline-state-updater updater-finished-ch)))))))
