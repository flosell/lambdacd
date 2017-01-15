(ns lambdacd.execution.internal.execute-step-test
  (:require [clojure.test :refer :all]
            [lambdacd.testsupport.data :refer [some-ctx-with some-ctx]]
            [lambdacd.testsupport.test-util :refer [events-for
                                                    slurp-chan
                                                    start-waiting-for
                                                    wait-for
                                                    step-running?
                                                    get-or-timeout]]
            [lambdacd.testsupport.matchers :refer [map-containing]]
            [lambdacd.testsupport.noop-pipeline-state :as noop-pipeline-state]
            [lambdacd.execution.internal.execute-step :refer :all]
            [clojure.core.async :as async]
            [clojure.core.async.impl.protocols :as async-protocols]))

(defn some-args []
  {})

(defn some-step-processing-input [arg & _]
  (assoc arg :foo :baz :status :success))

(defn some-successful-step [arg & _]
  {:status :success})

(defn some-other-step [arg & _]
  {:foo :baz :status :success})

(defn some-step-not-returning-status [arg & _]
  {})

(defn some-step-consuming-the-context [arg ctx]
  {:status :success :context-info (:the-info ctx)})

(def message-thrown-by-some-step-throwing-an-exception
  "Something went wrong!")

(defn some-step-throwing-an-exception [& _]
  (throw (Exception. ^String message-thrown-by-some-step-throwing-an-exception)))
(defn some-step-throwing-an-error [& _]
  (throw (Error. ^String message-thrown-by-some-step-throwing-an-exception)))

(defn some-step-building-up-result-state-incrementally [_ {c :result-channel}]
  (async/>!! c [:out "hello"])
  (async/>!! c [:out "hello world"])
  (async/>!! c [:some-value 42])
  (Thread/sleep 10) ; wait for a bit so that the success doesn't screw up the order of events tested
  {:status :success})

(defn some-step-building-up-result-state-incrementally-and-resetting [_ {c :result-channel}]
  (async/>!! c [:out "hello"])
  (async/>!! c [:some-value 42])
  (async/>!! c {:status :running :other-value 21})
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

(defn- step-finished-events-for [ctx]
  (events-for :step-finished ctx))

(defn- step-started-events-for [ctx]
  (events-for :step-started ctx))

(defn- step-result-updates-for [ctx]
  (events-for :step-result-updated ctx))



(deftest wrap-failure-if-no-status-test
  (testing "that a step-result with status will just be passed on"
    (is (= (some-successful-step (some-args) (some-ctx))
           ((wrap-failure-if-no-status some-successful-step) (some-args) (some-ctx)))))
  (testing "that a step without a status will be faileed"
    (is (= {:status :failure
            :out "step did not return any status!"}
           ((wrap-failure-if-no-status some-step-not-returning-status) (some-args) (some-ctx))))))

(deftest wrap-close-result-channel-test
  (testing "that the result-channel will be closed after the step is executed"
    (let [ctx (some-ctx)]
      ((wrap-close-result-channel some-other-step) (some-args) ctx)
      (is (async-protocols/closed? (:result-channel ctx)))))
  (testing "that the step result will not be touched"
    (is (= (some-successful-step (some-args) (some-ctx))
           ((wrap-close-result-channel some-successful-step) (some-args) (some-ctx))))))

(deftest wrap-exception-handling-test
  (testing "that exceptions thrown in the step will be caught and converted into proper failure step-results"
    (let [step-result ((wrap-exception-handling some-step-throwing-an-exception) (some-args) (some-ctx))]
      (is (= :failure (:status step-result)))
      (is (.contains (:out step-result) message-thrown-by-some-step-throwing-an-exception))))
  (testing "that a normal step result will not be touched"
    (is (= (some-successful-step (some-args) (some-ctx))
           ((wrap-exception-handling some-successful-step) (some-args) (some-ctx)))))
  (testing "that we don't catch Error"
    (is (thrown? Error ((wrap-exception-handling some-step-throwing-an-error) (some-args) (some-ctx))))))

(deftest wrap-convert-to-step-output-test
  (testing "that it converts the steps output into a different format" ;TODO: why exactly are we doing this?
    (is (= {:status :success
            :outputs {[1 2 3] {:status :success
                               :foo :baz}}} ((wrap-convert-to-step-output some-other-step) (some-args) (some-ctx-with :step-id [1 2 3]))))))

(deftest wrap-report-step-started-test
  (testing "that it sends a step-started event"
    (let [ctx                    (some-ctx-with :step-id [1]
                                                :build-number 2)
          step-started-events-ch (step-started-events-for ctx)]
      ((wrap-report-step-started some-other-step) (some-args) ctx)
      (is (= [{:step-id [1] :build-number 2}] (slurp-chan step-started-events-ch)))))
  (testing "that it reports an initial running step result"
    (let [ctx                    (some-ctx-with :step-id [1]
                                                :build-number 2)
          step-result-updates-ch (step-result-updates-for ctx)]
      ((wrap-report-step-started some-other-step) (some-args) ctx)
      (is (= {:step-id      [1]
              :build-number 2
              :step-result  {:status :running}} (first (slurp-chan step-result-updates-ch)))))))

(deftest wrap-report-step-started-stopped-test
  (testing "that it sends a step-finished event"
    (let [ctx                    (some-ctx-with :step-id [1]
                                                :build-number 2)
          step-finished-events-ch (step-finished-events-for ctx)]
      ((wrap-report-step-stopped some-other-step) (some-args) ctx)
      (is (= [{:step-id [1]
               :build-number 2
               :rerun-for-retrigger false
               :final-result {:status :success
                              :foo :baz}}] (slurp-chan step-finished-events-ch)))))
  (testing "that it reports a final step update"
    (let [ctx                    (some-ctx-with :step-id [1]
                                                :build-number 2)
          step-result-updates-ch (step-result-updates-for ctx)]
      ((wrap-report-step-stopped some-other-step) (some-args) ctx)
      (is (= {:step-id      [1]
              :build-number 2
              :step-result  {:status :success
                             :foo :baz}} (last (slurp-chan step-result-updates-ch)))))))

;(deftest wrap-async-step-result-handling-test) ; TODO: add tests here, possibly migrate from integration-level tests below

(deftest integrated-execute-step-test ; TODO: clean up if a few of those tests are redundant, already covered above
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
                  (let [ctx                  (some-ctx-with :step-id [0 0]
                                                            :build-number 5
                                                            :pipeline-state-component (noop-pipeline-state/new-no-op-pipeline-state))
                        step-results-channel (step-result-updates-for ctx)]
                    (execute-step {} [ctx some-successful-step])
                    (is (= [{ :build-number 5 :step-id [0 0] :step-result {:status :running } }
                            { :build-number 5 :step-id [0 0] :step-result {:status :success } }] (slurp-chan step-results-channel)))))
         (testing "that the final pipeline-state is properly set for a step returning a static and an async result"
                  (let [ctx                  (some-ctx-with :step-id [0 0]
                                                            :build-number 5
                                                            :pipeline-state-component (noop-pipeline-state/new-no-op-pipeline-state)
                                                            :config {:step-updates-per-sec nil})
                        step-results-channel (step-result-updates-for ctx)]
                    (execute-step {} [ctx some-step-that-sends-failure-on-ch-returns-success])
                    (is (= [{ :build-number 5 :step-id [0 0] :step-result {:status :running } }
                            { :build-number 5 :step-id [0 0] :step-result {:status :failure } }
                            { :build-number 5 :step-id [0 0] :step-result {:status :success } }] (slurp-chan step-results-channel)))))
         (testing "that the step result contains the static and the async output"
                  (is (= {:outputs {[0 0] {:status :success :some-value 42 :out "hello world"} } :status :success }
                         (execute-step {} [(some-ctx-with :step-id [0 0]) some-step-building-up-result-state-incrementally]))))
         (testing "that in doubt, the static output overlays the async output"
                  (is (= {:outputs {[0 0] {:status :success } } :status :success }
                         (execute-step {} [(some-ctx-with :step-id [0 0]) some-step-that-sends-failure-on-ch-returns-success]))))
         (testing "that the accumulated step-result is sent over the event-bus"
                  (let [ctx                  (some-ctx-with :step-id [0 0]
                                                            :build-number 5
                                                            :pipeline-state-component (noop-pipeline-state/new-no-op-pipeline-state)
                                                            :config {:step-updates-per-sec nil})
                        step-results-channel (step-result-updates-for ctx)]
                    (execute-step {} [ctx some-step-building-up-result-state-incrementally])
                    (is (= [{:build-number 5 :step-id [0 0] :step-result {:status :running}}
                            {:build-number 5 :step-id [0 0] :step-result {:status :running :out "hello"}}
                            {:build-number 5 :step-id [0 0] :step-result {:status :running :out "hello world"}}
                            {:build-number 5 :step-id [0 0] :step-result {:status :running :some-value 42 :out "hello world"}}
                            {:build-number 5 :step-id [0 0] :step-result {:status :success :some-value 42 :out "hello world"}}]
                           (slurp-chan step-results-channel)))))
         (testing "that the accumulated step-result is sent over the event-bus and can be resetted"
                  (let [ctx                  (some-ctx-with :step-id [0 0]
                                                            :build-number 5
                                                            :pipeline-state-component (noop-pipeline-state/new-no-op-pipeline-state)
                                                            :config {:step-updates-per-sec nil})
                        step-results-channel (step-result-updates-for ctx)]
                    (execute-step {} [ctx some-step-building-up-result-state-incrementally-and-resetting])
                    (is (= [{:build-number 5 :step-id [0 0] :step-result {:status :running}}
                            {:build-number 5 :step-id [0 0] :step-result {:status :running :out "hello"}}
                            {:build-number 5 :step-id [0 0] :step-result {:status :running :some-value 42 :out "hello"}}
                            {:build-number 5 :step-id [0 0] :step-result {:status :running :other-value 21}}
                            {:build-number 5 :step-id [0 0] :step-result {:status :success :other-value 21}}]
                           (slurp-chan step-results-channel)))))
         (testing "that the event bus is notified when a step finishes"
                  (let [ctx (some-ctx-with :build-number 3
                                           :step-id [1 2 3])
                        step-finished-events (step-finished-events-for ctx)]
                    (execute-step {} [ctx some-other-step])
                    (is (= [{:build-number 3
                             :step-id [1 2 3]
                             :final-result {:status :success :foo :baz}
                             :rerun-for-retrigger false}] (slurp-chan step-finished-events))))
                  (let [ctx (some-ctx-with :build-number 3
                                           :step-id [1 2 3]
                                           :retriggered-build-number 1
                                           :retriggered-step-id [0 1 2 3])
                        step-finished-events (step-finished-events-for ctx)]
                    (execute-step {} [ctx some-other-step])
                    (is (= [{:build-number 3
                             :step-id [1 2 3]
                             :final-result {:status :success :foo :baz}
                             :rerun-for-retrigger true}] (slurp-chan step-finished-events)))))
         (testing "that the event bus is notified when a step starts"
                  (let [ctx (some-ctx-with :build-number 3
                                           :step-id [1 2 3])
                        step-started-events (step-started-events-for ctx)]
                    (execute-step {} [ctx some-other-step])
                    (is (= [{:build-number 3
                             :step-id [1 2 3]}] (slurp-chan step-started-events))))))
