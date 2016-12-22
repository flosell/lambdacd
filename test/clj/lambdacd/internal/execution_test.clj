(ns lambdacd.internal.execution-test
  (:use [lambdacd.testsupport.test-util])
  (:require [clojure.test :refer :all]
            [lambdacd.internal.execution :refer :all]
            [lambdacd.testsupport.test-util :refer :all]
            [lambdacd.util.internal.async :refer [buffered]]
            [lambdacd.testsupport.matchers :refer :all]
            [lambdacd.steps.support :as step-support]
            [lambdacd.testsupport.data :refer [some-ctx-with some-ctx]]
            [lambdacd.testsupport.test-util :as tu]
            [clj-time.core :as t]
            [lambdacd.internal.execution :as execution]
            [lambdacd.state.core :as state]
            [lambdacd.util :as util]
            [lambdacd.execution.internal.execute-step :as execute-step]
            [lambdacd.util.internal.temp :as temp-util])
  (:import java.lang.IllegalStateException))

(defn some-step [arg & _]
  {:foo :baz})

(defn some-other-step [arg & _]
  {:foo :baz :status :success})

(defn some-step-returning-foobar-value [& _]
  {:foobar 42 :status :success})
(defn some-step-returning-ten-as-foobar-value [& _]
  {:foobar 10 :status :success})

(defn some-step-returning-global-foobar-value [& _]
  {:global {:foobar 42} :status :success})
(defn some-step-returning-another-global-foobar-value [& _]
  {:global {:foobar 43} :status :success})

(defn some-step-using-foobar-value [{foobar :foobar} & _]
  {:foobar-times-ten (* 10 foobar) :status :success})

(defn some-step-using-global-foobar-value [{{foobar :foobar} :global} & _]
  {:foobar-times-ten (* 10 foobar) :status :success})

(defn some-step-for-cwd [{cwd :cwd} & _]
  {:foo cwd :status :success})

(defn step-that-expects-a-kill-switch [_ {is-killed :is-killed}]
  {:status (if (nil? is-killed) :is-killed-not-there :success)})

(defn some-successful-step [arg & _]
  {:status :success})
(defn some-failing-step [arg & _]
  {:status :failure})

(defn some-step-caring-about-retrigger-metadata [_ {retriggered-build-number :retriggered-build-number retriggered-step-id :retriggered-step-id}]
  {:status :success :retriggered-build-number retriggered-build-number :retriggered-step-id retriggered-step-id})

(defn some-step-waiting-to-be-killed [_ ctx]
  (loop [counter 0]
    (step-support/if-not-killed ctx
                                (if (< counter 100) ;; make sure the step always eventually finishes
                                  (do
                                    (Thread/sleep 100)
                                    (recur (inc counter)))
                                  {:status :waited-too-long}))))

(with-private-fns [lambdacd.internal.execution [merge-two-step-results]]
  (deftest step-result-merge-test
    (testing "merging without collisions"
      (is (= {:foo "hello" :bar "world"} (merge-two-step-results {:foo "hello"} {:bar "world"}))))
    (testing "merging with value-collisions on keyword overwrites"
      (is (= {:status :failure} (merge-two-step-results {:status :success} {:status :failure}))))
    (testing "merging with value-collisions on keyword with values overwrites"
      (is (= {:exit 1} (merge-two-step-results {:exit 0 } {:exit 1}))))
    (testing "merging of nested maps"
      (is (= {:outputs {[1 0] {:foo :baz} [2 0] {:foo :baz}}}
             (merge-two-step-results {:outputs {[1 0] {:foo :baz}}} {:outputs { [2 0] {:foo :baz}}}))))
    (testing "merging into a flat list on collision"
      (is (= {:foo ["hello" "world" "test"]} (merge-two-step-results {:foo ["hello" "world"]} {:foo "test"}))))))

(defn some-pipeline-state []
  (atom {}))

(defn- step-result-updates-for [ctx]
  (events-for :step-result-updated ctx))

(deftest context-for-steps-test
  (testing "that we can generate proper contexts for steps and keep other context info as it is"
    (let [result (contexts-for-steps [some-step some-step] (some-ctx-with :some-value 42 :step-id [0]))]
      (is (= some-step (second (first result))))
      (is (= some-step (second (second result))))
      (is (= 42 (:some-value (first (first result)))))
      (is (= 42 (:some-value (first (second result)))))
      (is (= [1 0] (:step-id (first (first result)))))
      (is (= [2 0] (:step-id (first (second result))))))))

(deftest execute-steps-test
  (testing "that executing steps returns outputs of both steps with different step ids"
    (is (= {:outputs { [1 0] {:foo :baz :status :success} [2 0] {:foo :baz :status :success}} :status :success}
           (execute-steps [some-other-step some-other-step] {} (some-ctx-with :step-id [0])))))
  (testing "that a failing step prevents the succeeding steps from being executed"
    (is (= {:outputs { [1 0] {:status :success} [2 0] {:status :failure}} :status :failure}
           (execute-steps [some-successful-step some-failing-step some-other-step] {} (some-ctx-with :step-id [0])))))
  (testing "that the results of one step are the inputs to the other step"
    (is (= {:outputs { [1 0] {:status :success :foobar 42} [2 0] {:status :success :foobar-times-ten 420}} :status :success}
           (execute-steps [some-step-returning-foobar-value some-step-using-foobar-value] {} (some-ctx-with :step-id [0])))))
  (testing "that the original input is passed into all steps"
    (testing "the normal case"
      (is (= {:outputs { [1 0] {:status :success :foobar-times-ten 420} [2 0] {:status :success :foobar-times-ten 420}} :status :success}
             (execute-steps [some-step-using-foobar-value some-step-using-foobar-value] {:foobar 42} (some-ctx-with :step-id [0])))))
    (testing "step result overlays the original input"
      (is (= {:outputs { [1 0] {:status :success :foobar 10} [2 0] {:status :success :foobar-times-ten 100}} :status :success}
             (execute-steps [some-step-returning-ten-as-foobar-value some-step-using-foobar-value] {:foobar 42} (some-ctx-with :step-id [0]))))))
  (testing "that a steps :global results will be passed on to all subsequent steps"
    (is (= {:outputs {[1 0] {:status :success :global { :foobar 42}}
                      [2 0] {:status :success :foobar-times-ten 420}
                      [3 0] {:status :success :foobar-times-ten 420}}
            :status :success}
           (execute-steps [some-step-returning-global-foobar-value some-step-using-global-foobar-value some-step-using-global-foobar-value] {} (some-ctx-with :step-id [0])))))
  (testing "that a step overrides a :global value"
    (is (= {:outputs {[1 0] {:status :success :global {:foobar 42}}
                      [2 0] {:status :success :global {:foobar 43}}
                      [3 0] {:status :success :foobar-times-ten 430}}
            :status  :success}
           (execute-steps [some-step-returning-global-foobar-value some-step-returning-another-global-foobar-value some-step-using-global-foobar-value] {} (some-ctx-with :step-id [0])))))
  (testing "that execute steps injects a kill-switch by default"
    (is (= {:outputs { [1 0] {:status :success} [2 0] {:status :success}} :status :success}
           (execute-steps [some-successful-step step-that-expects-a-kill-switch] {} (some-ctx-with :step-id [0])))))
  (testing "that nil values (e.g. from an optional step in the structure) do not cause problems and are ignored"
    (is (= {:outputs { [1 0] {:status :success}} :status :success}
           (execute-steps [some-successful-step nil] {} (some-ctx-with :step-id [0]))))))

(defn some-control-flow [& _] ; just a mock, we don't actually execute this
  (fn [args ctx]
    (throw (IllegalStateException. "This shouldn't be called"))))

(defn some-step-that-fails-if-retriggered [ & _]
  (throw (IllegalStateException. "This step shouldn't be called")))

(defn some-control-flow-thats-called [& steps]
  (fn [arg ctx]
    (execution/execute-steps steps (assoc arg :some :val) ctx)))

(defn some-step-to-retrigger [args _]
  {:status :success :the-some (:some args)})

(deftest retrigger-test
  (testing "that retriggering results in a completely new pipeline-run where not all the steps are executed"
    (let [initial-state { 0 {[1] { :status :success }
                                   [1 1] {:status :success :out "I am nested"}
                                   [2] { :status :failure }}}
          pipeline `((some-control-flow some-step) some-successful-step)
          context (some-ctx-with :initial-pipeline-state initial-state)]
      (retrigger pipeline context 0 [2] 1)
      (wait-for (tu/step-success? context 1 [2]))
      (is (= {[1] { :status :success }
              [1 1] {:status :success :out "I am nested"}
              [2] { :status :failure }}
             (tu/without-ts (state/get-step-results context 0))))
      (is (= {[1] { :status :success :retrigger-mock-for-build-number 0 }
              [1 1] {:status :success :out "I am nested" :retrigger-mock-for-build-number 0}
              [2] { :status :success }}
             (tu/without-ts (state/get-step-results context 1))))))
  (testing "that retriggering consumes the pipeline structure for the new pipeline-run"
    (let [initial-state { 0 {[1] { :status :success }
                             [1 1] {:status :success :out "I am nested"}
                             [2] { :status :failure }}}
          pipeline `((some-control-flow some-step) some-successful-step)
          context (some-ctx-with :initial-pipeline-state initial-state)]
      (retrigger pipeline context 0 [2] 1)
      (wait-for (tu/step-success? context 1 [2]))
      (is (not= nil (state/get-pipeline-structure context 1)))))
  (testing "that we can retrigger a pipeline from the initial step as well"
    (let [pipeline `(some-successful-step some-other-step some-failing-step)
          initial-state { 0 {[1] {:status :to-be-retriggered}
                             [2] {:status :to-be-overwritten-by-next-run}
                             [3] {:status :to-be-overwritten-by-next-run}}}
          context (some-ctx-with :initial-pipeline-state initial-state)]
      (retrigger pipeline context 0 [1] 1)
      (wait-for (tu/step-failure? context 1 [3]))
      (is (= {[1] {:status :success}
              [2] {:status :success :foo :baz}
              [3] {:status :failure}}
             (tu/without-ts (state/get-step-results context 1))))))
  (testing "that steps after the retriggered step dont get the retrigger-metadata"
    (let [pipeline `(some-successful-step some-step-caring-about-retrigger-metadata)
          initial-state { 0 {[1] {:status :to-be-retriggered}
                             [2] {:status :to-be-overwritten-by-next-run}
                             [3] {:status :to-be-overwritten-by-next-run}}}
          context (some-ctx-with :initial-pipeline-state initial-state)]
      (retrigger pipeline context 0 [1] 1)
      (wait-for (tu/step-success? context 1 [2]))
      (is (= {[1] {:status :success}
              [2] {:status :success :retriggered-build-number nil :retriggered-step-id nil}}
             (tu/without-ts (state/get-step-results context 1))))))
  (testing "that retriggering works for nested steps"
    (let [initial-state { 0 {[1] { :status :success }
                             [1 1] {:status :success :out "I am nested"}
                             [2 1] {:status :unknown :out "this will be retriggered"}}}
          pipeline `((some-control-flow-thats-called some-step-that-fails-if-retriggered some-step-to-retrigger) some-successful-step)
          context (some-ctx-with :initial-pipeline-state initial-state)]
      (retrigger pipeline context 0 [2 1] 1)
      (wait-for (tu/step-success? context 1 [2]))
      (is (= {[1] { :status :success }
              [1 1] {:status :success :out "I am nested"}
              [2 1] {:status :unknown :out "this will be retriggered"}}
             (tu/without-ts (state/get-step-results context 0))))
      (is (= {[1] {:status :success
                   :outputs {[1 1] {:status :success :out "I am nested" :retrigger-mock-for-build-number 0 }
                             [2 1] {:the-some :val :status :success }}}
              [1 1] {:status :success :out "I am nested" :retrigger-mock-for-build-number 0}
              [2 1] {:the-some :val :status :success}
              [2] { :status :success }}
             (tu/without-ts (state/get-step-results context 1))))))
  (testing "that retriggering updates timestamps of container steps (#56)"
    (let [initial-state { 0 {[1] { :status :unknown :first-updated-at (t/date-time 1970) }
                             [1 1] {:status :success :out "I am nested"}
                             [2 1] {:status :unknown :out "this will be retriggered"}}}
          pipeline `((some-control-flow-thats-called some-step-that-fails-if-retriggered some-step-to-retrigger) some-successful-step)
          context (some-ctx-with :initial-pipeline-state initial-state)]
      (retrigger pipeline context 0 [2 1] 1)
      (wait-for (tu/step-success? context 1 [2]))
      (let [new-container-step-result (state/get-step-result context 1 [1])]
        (is (= :success (:status new-container-step-result)))
        (is (not= 1970 (t/year (:first-updated-at new-container-step-result))))))))

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


(deftest kill-all-pipelines-test
  (testing "that it kills root build steps in any pipeline"
    (let [ctx                (some-ctx-with :step-id [3])
          future-step-result (start-waiting-for (execute-step/execute-step {} [ctx some-step-waiting-to-be-killed]))]
      (wait-for (tu/step-running? ctx))
      (kill-all-pipelines ctx)
      (is (map-containing {:status :killed} (get-or-timeout future-step-result :timeout 1000)))))
  (testing "that it doesn't kill nested steps as they are killed by their parents"
    (let [ctx                (some-ctx-with :step-id [3 1])
          future-step-result (start-waiting-for (execute-step/execute-step {} [ctx some-step-waiting-to-be-killed]))]
      (wait-for (tu/step-running? ctx))
      (kill-all-pipelines ctx)
      (is (map-containing {:status :timeout} (get-or-timeout future-step-result :timeout 1000)))))
  (testing "that killing is idempotent"
    (let [ctx                (some-ctx-with :step-id [3])
          future-step-result (start-waiting-for (execute-step/execute-step {} [ctx some-step-waiting-to-be-killed]))]
      (wait-for (tu/step-running? ctx))
      (kill-all-pipelines ctx)
      (kill-all-pipelines ctx)
      (is (map-containing {:status :killed} (get-or-timeout future-step-result :timeout 1000)))
      (kill-all-pipelines ctx)))
  (testing "that it waits until all running pipelines are done"
    (let [started-steps (atom #{:foo})
          ctx           (some-ctx-with :step-id [3 1]
                                       :started-steps started-steps)]
      ; still running, should time out
      (is (map-containing {:status :timeout} (call-with-timeout 300 (kill-all-pipelines ctx))))
      (reset! started-steps #{})
      (is (not= {:status :timeout} (call-with-timeout 500 (kill-all-pipelines ctx))))))
  (testing "that waiting for pipeline completion times out"
    (let [started-steps (atom #{:foo})
          ctx           (some-ctx-with :step-id [3 1]
                                       :started-steps started-steps
                                       :config {:ms-to-wait-for-shutdown 200
                                                :home-dir                (temp-util/create-temp-dir)})]
      (is (not= {:status :timeout} (call-with-timeout 1000 (kill-all-pipelines ctx)))))))
