(ns lambdacd.execution.internal.retrigger-test
  (:import java.lang.IllegalStateException)
  (:require [clojure.test :refer :all]
            [lambdacd.execution.internal.retrigger :refer :all]
            [lambdacd.execution.core :as execution-core]
            [lambdacd.testsupport.test-util :refer [without-ts
                                                    step-success?
                                                    slurp-chan
                                                    wait-for
                                                    events-for
                                                    step-failure?]]
            [lambdacd.util.internal.async :refer [buffered]]
            [lambdacd.testsupport.matchers :refer :all]
            [lambdacd.testsupport.data :refer [some-ctx-with some-ctx]]
            [lambdacd.execution.internal.execute-steps :as execute-steps]
            [clj-time.core :as t]
            [lambdacd.state.core :as state]))

(defn- step-result-updates-for [ctx]
  (events-for :step-result-updated ctx))

(defn some-step [arg & _]
  {:foo :baz})

(defn some-other-step [arg & _]
  {:foo :baz :status :success})


(defn some-step-for-cwd [{cwd :cwd} & _]
  {:foo cwd :status :success})

(defn some-successful-step [arg & _]
  {:status :success})
(defn some-failing-step [arg & _]
  {:status :failure})

(defn some-step-caring-about-retrigger-metadata [_ {retriggered-build-number :retriggered-build-number retriggered-step-id :retriggered-step-id}]
  {:status :success :retriggered-build-number retriggered-build-number :retriggered-step-id retriggered-step-id})


(defn some-pipeline-state []
  (atom {}))

(defn some-control-flow [& _] ; just a mock, we don't actually execute this
  (fn [args ctx]
    (throw (IllegalStateException. "This shouldn't be called"))))

(defn some-step-that-fails-if-retriggered [ & _]
  (throw (IllegalStateException. "This step shouldn't be called")))

(defn some-control-flow-thats-called [& steps]
  (fn [arg ctx]
    (execute-steps/execute-steps steps (assoc arg :some :val) ctx)))

(defn some-step-to-retrigger [args _]
  {:status :success :the-some (:some args)})

(deftest retrigger-mock-step-test
  (testing "that it returns the result of the original step"
    (let [some-original-result {:foo :bar}
          initial-state        { 0 {[1] some-original-result
                                    [2] {:some :other :steps :result}}
                                1 {[1] {:some :other :builds :result}}}
          ctx                  (some-ctx-with :initial-pipeline-state initial-state
                                              :step-id [1])
          mock-step            (retrigger-mock-step 0)]
      (is (= (assoc some-original-result :retrigger-mock-for-build-number 0)
             (mock-step {} ctx)))))
  (testing "that it reports the results of its children"
    (let [some-original-result  {:foo :bar}
          some-childs-result    {:bar :baz}
          initial-state         {0 {[1] some-original-result
                                    [1 1] some-childs-result
                                    [2] {:some :other :steps :result}}
                                 1 {[1] {:some :other :builds :result}}}
          ctx                   (some-ctx-with :initial-pipeline-state initial-state
                                               :build-number 1
                                               :step-id [1])
          step-finished-events  (step-result-updates-for ctx)
          mock-step             (retrigger-mock-step 0)
          expected-child-result (assoc some-childs-result :retrigger-mock-for-build-number 0)]
      (mock-step {} ctx)
      (is (= [{:build-number 1
               :step-id      [1 1]
               :step-result expected-child-result}]
             (slurp-chan step-finished-events))))))

(deftest retrigger-test
         (testing "that retriggering results in a completely new pipeline-run where not all the steps are executed"
                  (let [initial-state { 0 {[1] { :status :success }
                                           [1 1] {:status :success :out "I am nested"}
                                           [2] { :status :failure }}}
                        pipeline `((some-control-flow some-step) some-successful-step)
                        context (some-ctx-with :initial-pipeline-state initial-state)]
                    (execution-core/retrigger-pipeline pipeline context 0 [2] 1)
                    (wait-for (step-success? context 1 [2]))
                    (is (= {[1] { :status :success }
                            [1 1] {:status :success :out "I am nested"}
                            [2] { :status :failure }}
                           (without-ts (state/get-step-results context 0))))
                    (is (= {[1] { :status :success :retrigger-mock-for-build-number 0 }
                            [1 1] {:status :success :out "I am nested" :retrigger-mock-for-build-number 0}
                            [2] { :status :success }}
                           (without-ts (state/get-step-results context 1))))))
         (testing "that retriggering consumes the pipeline structure for the new pipeline-run"
                  (let [initial-state { 0 {[1] { :status :success }
                                           [1 1] {:status :success :out "I am nested"}
                                           [2] { :status :failure }}}
                        pipeline `((some-control-flow some-step) some-successful-step)
                        context (some-ctx-with :initial-pipeline-state initial-state)]
                    (execution-core/retrigger-pipeline pipeline context 0 [2] 1)
                    (wait-for (step-success? context 1 [2]))
                    (is (not= nil (state/get-pipeline-structure context 1)))))
         (testing "that we can retrigger a pipeline from the initial step as well"
                  (let [pipeline `(some-successful-step some-other-step some-failing-step)
                        initial-state { 0 {[1] {:status :to-be-retriggered}
                                           [2] {:status :to-be-overwritten-by-next-run}
                                           [3] {:status :to-be-overwritten-by-next-run}}}
                        context (some-ctx-with :initial-pipeline-state initial-state)]
                    (execution-core/retrigger-pipeline pipeline context 0 [1] 1)
                    (wait-for (step-failure? context 1 [3]))
                    (is (= {[1] {:status :success}
                            [2] {:status :success :foo :baz}
                            [3] {:status :failure}}
                           (without-ts (state/get-step-results context 1))))))
         (testing "that steps after the retriggered step dont get the retrigger-metadata"
                  (let [pipeline `(some-successful-step some-step-caring-about-retrigger-metadata)
                        initial-state { 0 {[1] {:status :to-be-retriggered}
                                           [2] {:status :to-be-overwritten-by-next-run}
                                           [3] {:status :to-be-overwritten-by-next-run}}}
                        context (some-ctx-with :initial-pipeline-state initial-state)]
                    (execution-core/retrigger-pipeline pipeline context 0 [1] 1)
                    (wait-for (step-success? context 1 [2]))
                    (is (= {[1] {:status :success}
                            [2] {:status :success :retriggered-build-number nil :retriggered-step-id nil}}
                           (without-ts (state/get-step-results context 1))))))
         (testing "that retriggering works for nested steps"
                  (let [initial-state { 0 {[1] { :status :success }
                                           [1 1] {:status :success :out "I am nested"}
                                           [2 1] {:status :unknown :out "this will be retriggered"}}}
                        pipeline `((some-control-flow-thats-called some-step-that-fails-if-retriggered some-step-to-retrigger) some-successful-step)
                        context (some-ctx-with :initial-pipeline-state initial-state)]
                    (execution-core/retrigger-pipeline pipeline context 0 [2 1] 1)
                    (wait-for (step-success? context 1 [2]))
                    (is (= {[1] { :status :success }
                            [1 1] {:status :success :out "I am nested"}
                            [2 1] {:status :unknown :out "this will be retriggered"}}
                           (without-ts (state/get-step-results context 0))))
                    (is (= {[1] {:status :success
                                 :outputs {[1 1] {:status :success :out "I am nested" :retrigger-mock-for-build-number 0 }
                                           [2 1] {:the-some :val :status :success }}}
                            [1 1] {:status :success :out "I am nested" :retrigger-mock-for-build-number 0}
                            [2 1] {:the-some :val :status :success}
                            [2] { :status :success }}
                           (without-ts (state/get-step-results context 1))))))
         (testing "that retriggering updates timestamps of container steps (#56)"
                  (let [initial-state { 0 {[1] { :status :unknown :first-updated-at (t/date-time 1970) }
                                           [1 1] {:status :success :out "I am nested"}
                                           [2 1] {:status :unknown :out "this will be retriggered"}}}
                        pipeline `((some-control-flow-thats-called some-step-that-fails-if-retriggered some-step-to-retrigger) some-successful-step)
                        context (some-ctx-with :initial-pipeline-state initial-state)]
                    (execution-core/retrigger-pipeline pipeline context 0 [2 1] 1)
                    (wait-for (step-success? context 1 [2]))
                    (let [new-container-step-result (state/get-step-result context 1 [1])]
                      (is (= :success (:status new-container-step-result)))
                      (is (not= 1970 (t/year (:first-updated-at new-container-step-result))))))))
