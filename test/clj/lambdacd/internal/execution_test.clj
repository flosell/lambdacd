(ns lambdacd.internal.execution-test
  (:use [lambdacd.testsupport.test-util])
  (:require [clojure.test :refer :all]
            [lambdacd.internal.execution :refer :all]
            [lambdacd.testsupport.test-util :refer :all]
            [lambdacd.util :refer [buffered]]
            [lambdacd.testsupport.matchers :refer :all]
            [clojure.core.async :as async]
            [lambdacd.steps.support :as step-support]
            [lambdacd.testsupport.data :refer [some-ctx-with some-ctx]]
            [lambdacd.testsupport.test-util :as tu]
            [lambdacd.internal.execution :as execution]
            [lambdacd.testsupport.noop-pipeline-state :as noop-pipeline-state]
            [lambdacd.internal.pipeline-state :as ps]
            [lambdacd.event-bus :as event-bus])
  (:import java.lang.IllegalStateException))

(defn some-step-processing-input [arg & _]
  (assoc arg :foo :baz :status :success))

(defn some-step [arg & _]
  {:foo :baz})

(defn some-other-step [arg & _]
  {:foo :baz :status :success})

(defn some-step-faking-events-from-build-3 [arg ctx]
  (event-bus/publish ctx :step-result-updated {:step-id [1 0] :build-number 3 :step-result {:message-from "other build"}})
  {:status :success})

(defn some-step-returning-foobar-value [& _]
  {:foobar 42 :status :success})
(defn some-step-returning-ten-as-foobar-value [& _]
  {:foobar 10 :status :success})

(defn some-step-returning-global-foobar-value [& _]
  {:global {:foobar 42} :status :success})

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

(defn some-step-not-returning-status [arg & _]
  {})

(defn some-step-consuming-the-context [arg ctx]
  {:status :success :context-info (:the-info ctx)})

(defn some-step-throwing-an-exception [& _]
  (throw (Throwable. "Something went wrong!")))

(defn some-step-building-up-result-state-incrementally [_ {c :result-channel}]
  (async/>!! c [:out "hello"])
  (async/>!! c [:out "hello world"])
  (async/>!! c [:some-value 42])
  (Thread/sleep 10) ; wait for a bit so that the success doesn't screw up the order of events tested
  {:status :success})

(defn some-step-sending-a-wait [_ {c :result-channel}]
  (async/>!! c [:status :waiting])
  (Thread/sleep 10) ; wait for a bit so that the success doesn't screw up the order of events tested
  {:status :success})

(defn some-step-that-sends-failure-on-ch-returns-success [_ {c :result-channel}]
  (async/>!! c [:status :failure])
  {:status :success})

(defn some-step-writing-to-the-result-channel [_ ctx]
  (let [result-ch (:result-channel ctx)]
    (async/>!! result-ch [:out "hello world"])
    {:status :success}))

(defn some-step-waiting-to-be-killed [_ ctx]
  (loop [counter 0]
    (step-support/if-not-killed ctx
                                (if (< counter 100) ;; make sure the step always eventually finishes
                                  (do
                                    (Thread/sleep 100)
                                    (recur (inc counter)))
                                  {:status :waited-too-long}))))

(defn some-step-flipping-the-kill-switch [_ {is-killed :is-killed}]
  (reset! is-killed true))

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

(deftest execute-step-test
  (testing "that executing returns the step result added to the input args"
    (is (= {:outputs { [0 0] {:foo :baz :x :y :status :success}} :status :success} (execute-step {:x :y} [(some-ctx-with :step-id [0 0]) some-step-processing-input]))))
  (testing "that executing returns the steps result-status as a special field and leaves it in the output as well"
    (is (= {:outputs { [0 0] {:status :success} } :status :success} (execute-step {} [(some-ctx-with :step-id [0 0]) some-successful-step]))))
  (testing "that we treat steps returning no status as failures"
    (is (= {:outputs { [0 0] {:status :failure :out "step did not return any status!"} } :status :failure}
           (execute-step {} [(some-ctx-with :step-id [0 0]) some-step-not-returning-status] ))))
  (testing "that the result indicates that a step has been waiting"
    (is (= {:outputs { [0 0] {:status :success :has-been-waiting true}} :status :success} (execute-step {} [(some-ctx-with :step-id [0 0]) some-step-sending-a-wait] ))))
  (testing "that if an exception is thrown in the step, it will result in a failure and the exception output is logged"
    (let [output (execute-step {} [(some-ctx-with :step-id [0 0]) some-step-throwing-an-exception])]
      (is (= :failure (get-in output [:outputs [0 0] :status])))
      (is (.contains (get-in output [:outputs [0 0] :out]) "Something went wrong"))))
  (testing "that the context passed to the step contains an output-channel and that results passed into this channel are merged into the result"
    (is (= {:outputs { [0 0] {:out "hello world" :status :success}} :status :success} (execute-step {} [(some-ctx-with :step-id [0 0]) some-step-writing-to-the-result-channel]))))
  (testing "that the context data is being passed on to the step"
    (is (= {:outputs { [0 0] {:status :success :context-info "foo"}} :status :success} (execute-step {} [(some-ctx-with :step-id [0 0] :the-info "foo") some-step-consuming-the-context]))))
  (testing "that the final pipeline-state is properly set for a step returning a static result"
    (let [step-results-channel (async/chan 100)]
      (execute-step {} [(some-ctx-with :step-id [0 0]
                                       :build-number 5
                                       :step-results-channel step-results-channel
                                       :pipeline-state-component (noop-pipeline-state/new-no-op-pipeline-state)) some-successful-step])
      (is (= [{ :build-number 5 :step-id [0 0] :step-result {:status :running } }
              { :build-number 5 :step-id [0 0] :step-result {:status :success } }] (slurp-chan step-results-channel)))))
  (testing "that the final pipeline-state is properly set for a step returning a static and an async result"
    (let [step-results-channel (async/chan 100)]
      (execute-step {} [(some-ctx-with :step-id [0 0]
                                       :build-number 5
                                       :step-results-channel step-results-channel
                                       :pipeline-state-component (noop-pipeline-state/new-no-op-pipeline-state)) some-step-that-sends-failure-on-ch-returns-success])
      (is (= [{ :build-number 5 :step-id [0 0] :step-result {:status :running } }
              { :build-number 5 :step-id [0 0] :step-result {:status :failure } }
              { :build-number 5 :step-id [0 0] :step-result {:status :success } }] (slurp-chan step-results-channel)))))
  (testing "that the step result contains the static and the async output"
    (is (= {:outputs {[0 0] {:status :success :some-value 42 :out "hello world"} } :status :success }
           (execute-step {} [(some-ctx-with :step-id [0 0]) some-step-building-up-result-state-incrementally]))))
  (testing "that in doubt, the static output overlays the async output"
    (is (= {:outputs {[0 0] {:status :success } } :status :success }
           (execute-step {} [(some-ctx-with :step-id [0 0]) some-step-that-sends-failure-on-ch-returns-success]))))
  (testing "that we can pass in a step-results-channel that receives messages with the complete, accumulated step result"
    (let [step-results-channel (async/chan 100)]
      (execute-step {} [(some-ctx-with :step-id [0 0]
                                       :build-number 5
                                       :step-results-channel step-results-channel
                                       :pipeline-state-component (noop-pipeline-state/new-no-op-pipeline-state)) some-step-building-up-result-state-incrementally])
    (is (= [{:build-number 5 :step-id [0 0] :step-result {:status :running } }
            {:build-number 5 :step-id [0 0] :step-result {:status :running :out "hello"} }
            {:build-number 5 :step-id [0 0] :step-result {:status :running :out "hello world"} }
            {:build-number 5 :step-id [0 0] :step-result {:status :running :some-value 42 :out "hello world"} }
            {:build-number 5 :step-id [0 0] :step-result {:status :success :some-value 42 :out "hello world"} }] (slurp-chan step-results-channel)))))
  (testing "that the accumulated step-result is sent over the event-bus"
    (let [ctx (some-ctx-with :step-id [0 0]
                             :build-number 5
                             :pipeline-state-component (noop-pipeline-state/new-no-op-pipeline-state))
          step-results-channel (-> (event-bus/subscribe ctx :step-result-updated)
                                   (event-bus/only-payload)
                                   (buffered))]
      (execute-step {} [ctx some-step-building-up-result-state-incrementally])
    (is (= [{:build-number 5 :step-id [0 0] :step-result {:status :running } }
            {:build-number 5 :step-id [0 0] :step-result {:status :running :out "hello"} }
            {:build-number 5 :step-id [0 0] :step-result {:status :running :out "hello world"} }
            {:build-number 5 :step-id [0 0] :step-result {:status :running :some-value 42 :out "hello world"} }
            {:build-number 5 :step-id [0 0] :step-result {:status :success :some-value 42 :out "hello world"} }] (slurp-chan step-results-channel)))))
  (testing "that the event bus is notified when a step finishes"
    (let [ctx (some-ctx-with :build-number 3
                             :step-id [1 2 3])
          step-finished-events (-> (event-bus/subscribe ctx :step-finished)
                                   (event-bus/only-payload)
                                   (buffered))]
      (execute-step {} [ctx some-other-step])
      (is (= [{:build-number 3
               :step-id [1 2 3]
               :final-result {:status :success :foo :baz}}] (slurp-chan step-finished-events)))))
  (testing "that a running step can be killed"
    (let [is-killed (atom false)
          ctx (some-ctx-with :step-id [3 2 1]
                             :build-number 3
                             :is-killed is-killed)
          future-step-result (start-waiting-for (execute-step {} [ctx some-step-waiting-to-be-killed]))]
      (Thread/sleep 100) ; make sure the step is running
      (kill-step ctx 3 [3 2 1])
      (is (map-containing {:status :killed} (get-or-timeout future-step-result)))))
  (testing "that a step using the kill-switch does not bubble up to the parents passing in the kill-switch"
    (let [is-killed (atom false)
          ctx (some-ctx-with :is-killed is-killed)]
      (execute-step {} [ctx some-step-flipping-the-kill-switch])
      (is (= false @is-killed)))))

(deftest context-for-steps-test
  (testing "that we can generate proper contexts for steps and keep other context info as it is"
    (let [step-results-channel (async/chan 42)
          result (contexts-for-steps [some-step some-step] (some-ctx-with :some-value 42 :step-id [0]) step-results-channel)]
      (is (= some-step (second (first result))))
      (is (= some-step (second (second result))))
      (is (= 42 (:some-value (first (first result)))))
      (is (= 42 (:some-value (first (second result)))))
      (is (= step-results-channel (:step-results-channel (first (first result)))))
      (is (= [1 0] (:step-id (first (first result)))))
      (is (= [2 0] (:step-id (first (second result)))))
      (is (= step-results-channel (:step-results-channel (first (second result))))))))

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
  (testing "that execute steps injects a kill-switch by default"
    (is (= {:outputs { [1 0] {:status :success} [2 0] {:status :success}} :status :success}
           (execute-steps [some-successful-step step-that-expects-a-kill-switch] {} (some-ctx-with :step-id [0]))))))

(deftest execute-steps-inheritance-test
  (testing "that the step-results channel passed in contains the step-results of all childrens"
    (let [step-results-channel (async/chan 100)]
      (execute-steps [some-other-step some-step-faking-events-from-build-3 some-failing-step]
                     {}
                     (some-ctx-with :step-results-channel step-results-channel
                                    :step-id [0]
                                    :build-number 2))
      (is (= [{:build-number 2 :step-id [1 0] :step-result {:status :running}}
              {:build-number 2 :step-id [1 0] :step-result {:foo :baz :status :success}}
              {:build-number 2 :step-id [2 0] :step-result {:status :running}}
              {:build-number 2 :step-id [2 0] :step-result {:status :success}}
              {:build-number 2 :step-id [3 0] :step-result {:status :running}}
              {:build-number 2 :step-id [3 0] :step-result {:status :failure}}] (slurp-chan step-results-channel))))))

(defn some-control-flow [&] ; just a mock, we don't actually execute this
  (throw (IllegalStateException. "This shouldn't be called")))

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
      (Thread/sleep 200)
      (is (= {0 {[1] { :status :success }
                 [1 1] {:status :success :out "I am nested"}
                 [2] { :status :failure }}
              1 {[1] { :status :success :retrigger-mock-for-build-number 0 }
                 [1 1] {:status :success :out "I am nested" :retrigger-mock-for-build-number 0}
                 [2] { :status :success }}} (tu/without-ts (ps/get-all (:pipeline-state-component context)))))))
  (testing "that we can retrigger a pipeline from the initial step as well"
    (let [pipeline `(some-successful-step some-other-step some-failing-step)
          context (some-ctx)]
      (retrigger pipeline context 0 [1] 1)
      (Thread/sleep 200)
      (is (= {1 {[1] { :status :success}
                 [2] {:status :success :foo :baz}
                 [3] { :status :failure }}} (tu/without-ts (ps/get-all (:pipeline-state-component context)))))))
  (testing "that retriggering works for nested steps"
    (let [initial-state { 0 {[1] { :status :success }
                             [1 1] {:status :success :out "I am nested"}
                             [2 1] {:status :unknown :out "this will be retriggered"}}}
          pipeline `((some-control-flow-thats-called some-step-that-fails-if-retriggered some-step-to-retrigger) some-successful-step)
          context (some-ctx-with :initial-pipeline-state initial-state)]
      (retrigger pipeline context 0 [2 1] 1)
      (Thread/sleep 200)
      (is (= {0 {[1] { :status :success }
                 [1 1] {:status :success :out "I am nested"}
                 [2 1] {:status :unknown :out "this will be retriggered"}}
              1 {[1] {:status :success
                      :outputs {[1 1] {:status :success :out "I am nested" :retrigger-mock-for-build-number 0 }
                                [2 1] {:the-some :val :status :success }}
                      :retrigger-mock-for-build-number 0 }
                 [1 1] {:status :success :out "I am nested" :retrigger-mock-for-build-number 0}
                 [2 1] {:the-some :val :status :success}
                 [2] { :status :success }}} (tu/without-ts (ps/get-all (:pipeline-state-component context))))))))
