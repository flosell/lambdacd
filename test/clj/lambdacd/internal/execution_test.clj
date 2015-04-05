(ns lambdacd.internal.execution-test
  (:use [lambdacd.testsupport.test-util])
  (:require [clojure.test :refer :all]
            [lambdacd.internal.execution :refer :all]
            [lambdacd.testsupport.test-util :refer [eventually]]
            [clojure.core.async :as async]))

(defn some-step-processing-input [arg & _]
  (assoc arg :foo :baz :status :success))

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
  (async/>!! c [:status :success])
  (Thread/sleep 10) ; wait for a bit so that the success doesn't screw up the order of events tested
  {:status :success})

(defn some-step-that-sends-failure-on-ch-returns-success [_ {c :result-channel}]
  (async/>!! c [:status :failure])
  {:status :success})

(defn some-step-writing-to-the-result-channel [_ ctx]
  (let [result-ch (:result-channel ctx)]
    (async/>!! result-ch [:out "hello world"])
    {:status :success}))

(with-private-fns [lambdacd.internal.execution [merge-step-results]]
  (deftest step-result-merge-test
    (testing "merging without collisions"
      (is (= {:foo "hello" :bar "world"} (merge-step-results {:foo "hello"} {:bar "world"}))))
    (testing "merging with value-collisions on keyword overwrites"
      (is (= {:status :failure} (merge-step-results {:status :success} {:status :failure}))))
    (testing "merging of nested maps"
      (is (= {:outputs {[1 0] {:foo :baz} [2 0] {:foo :baz}}}
             (merge-step-results {:outputs {[1 0] {:foo :baz}}} {:outputs { [2 0] {:foo :baz}}}))))
    (testing "merging into a flat list on collision"
      (is (= {:foo ["hello" "world" "test"]} (merge-step-results {:foo ["hello" "world"]} {:foo "test"}))))))

(deftest execute-step-test
  (testing "that executing returns the step result added to the input args"
    (is (= {:outputs { [0 0] {:foo :baz :x :y :status :success}} :status :success} (execute-step some-step-processing-input {:x :y} {:step-id [0 0]}))))
  (testing "that executing returns the steps result-status as a special field and leaves it in the output as well"
    (is (= {:outputs { [0 0] {:status :success} } :status :success} (execute-step some-successful-step {} {:step-id [0 0]}))))
  (testing "that the result-status is :undefined if the step doesn't return any"
    (is (= {:outputs { [0 0] {:status :undefined} } :status :undefined} (execute-step some-step-not-returning-status {} {:step-id [0 0]}))))
  (testing "that if an exception is thrown in the step, it will result in a failure and the exception output is logged"
    (let [output (execute-step some-step-throwing-an-exception {} {:step-id [0 0]})]
      (is (= :failure (get-in output [:outputs [0 0] :status])))
      (is (.contains (get-in output [:outputs [0 0] :out]) "Something went wrong"))))
  (testing "that the context passed to the step contains an output-channel and that results passed into this channel are merged into the result"
    (is (= {:outputs { [0 0] {:out "hello world" :status :success}} :status :success} (execute-step some-step-writing-to-the-result-channel {} {:step-id [0 0]}))))
  (testing "that the context data is being passed on to the step"
    (is (= {:outputs { [0 0] {:status :success :context-info "foo"}} :status :success} (execute-step some-step-consuming-the-context {} {:step-id [0 0] :the-info "foo"}))))
  (testing "that the final pipeline-state is properly set for a step returning a static result"
    (let [pipeline-state-atom (atom {})]
      (is (= { 5 { [0 0] {:status :success } } }
             (do (execute-step some-successful-step {} {:step-id [0 0] :build-number 5 :_pipeline-state pipeline-state-atom})
                 @pipeline-state-atom)))))
  (testing "that the final pipeline-state is properly set for a step returning a static and an async result"
    (let [pipeline-state-atom (atom {})]
      (is (= { 5 { [0 0] {:status :success } } }
             (do (execute-step some-step-that-sends-failure-on-ch-returns-success {} {:step-id [0 0] :build-number 5 :_pipeline-state pipeline-state-atom})
                 @pipeline-state-atom)))))
  (testing "that the pipeline-state is updated over time"
    (let [pipeline-state (atom {})]
      (is (= [{ 5 { [0 0] {:status :running } } }
              { 5 { [0 0] {:status :running :out "hello"} } }
              { 5 { [0 0] {:status :running :out "hello world"} } }
              { 5 { [0 0] {:status :running :some-value 42 :out "hello world"} } }
              { 5 { [0 0] {:status :success :some-value 42 :out "hello world"} } }]
             (atom-history-for pipeline-state
               (execute-step some-step-building-up-result-state-incrementally {} {:step-id [0 0] :build-number 5 :_pipeline-state pipeline-state}))))))
  (testing "that the step result contains the static and the async output"
    (let [pipeline-state (atom {})]
      (is (= {:outputs {[0 0] {:status :success :some-value 42 :out "hello world"} } :status :success }
             (execute-step some-step-building-up-result-state-incrementally {} {:step-id [0 0] :build-number 5 :_pipeline-state pipeline-state})))))
  (testing "that in doubt, the static output overlays the async output"
    (let [pipeline-state (atom {})]
      (is (= {:outputs {[0 0] {:status :success } } :status :success }
             (execute-step some-step-that-sends-failure-on-ch-returns-success {} {:step-id [0 0] :build-number 5 :_pipeline-state pipeline-state})))))

  )



(deftest context-for-steps-test
  (testing "that we can generate proper contexts for steps and keep other context info as it is"
    (is (= [[{:some-value 42 :step-id [1 0]} some-step] [{:some-value 42 :step-id [2 0]} some-step]] (contexts-for-steps [some-step some-step] {:some-value 42 :step-id [0 0]})))))

(deftest execute-steps-test
  (testing "that executing steps returns outputs of both steps with different step ids"
    (is (= {:outputs { [1 0] {:foo :baz :status :success} [2 0] {:foo :baz :status :success}} :status :success}
           (execute-steps [some-other-step some-other-step] {} { :step-id [0 0] }))))
  (testing "that a failing step prevents the succeeding steps from being executed"
    (is (= {:outputs { [1 0] {:status :success} [2 0] {:status :failure}} :status :failure}
           (execute-steps [some-successful-step some-failing-step some-other-step] {} { :step-id [0 0] }))))
  (testing "that the results of one step are the inputs to the other step"
    (is (= {:outputs { [1 0] {:status :success :foobar 42} [2 0] {:status :success :foobar-times-ten 420}} :status :success}
           (execute-steps [some-step-returning-foobar-value some-step-using-foobar-value] {} { :step-id [0 0] }))))
  (testing "that the original input is passed into all steps"
    (testing "the normal case"
      (is (= {:outputs { [1 0] {:status :success :foobar-times-ten 420} [2 0] {:status :success :foobar-times-ten 420}} :status :success}
             (execute-steps [some-step-using-foobar-value some-step-using-foobar-value] {:foobar 42} { :step-id [0 0] }))))
    (testing "step result overlays the original input"
      (is (= {:outputs { [1 0] {:status :success :foobar 10} [2 0] {:status :success :foobar-times-ten 100}} :status :success}
             (execute-steps [some-step-returning-ten-as-foobar-value some-step-using-foobar-value] {:foobar 42} { :step-id [0 0] })))))
  (testing "that a steps :global results will be passed on to all subsequent steps"
    (is (= {:outputs {[1 0] {:status :success :global { :foobar 42}}
                      [2 0] {:status :success :foobar-times-ten 420}
                      [3 0] {:status :success :foobar-times-ten 420}}
            :status :success}
           (execute-steps [some-step-returning-global-foobar-value some-step-using-global-foobar-value some-step-using-global-foobar-value] {} { :step-id [0 0] }))))
  (testing "that execute steps injects a kill-switch by default"
    (is (= {:outputs { [1 0] {:status :success} [2 0] {:status :success}} :status :success}
           (execute-steps [some-successful-step step-that-expects-a-kill-switch] {} { :step-id [0 0] })))))

(defn some-control-flow [&] ; just a mock, we don't actually execute this
  )

(deftest retrigger-test
  (testing "that retriggering results in a completely new pipeline-run where not all the steps are executed"
    (let [pipeline-state-atom (atom { 0 {[1] { :status :success }
                                         [1 1] {:status :success :out "I am nested"}
                                         [2] { :status :failure }}})
          pipeline `((some-control-flow some-step) some-successful-step)
          context { :_pipeline-state pipeline-state-atom}]
      (retrigger pipeline context 0 [2])
      (is (= {0 {[1] { :status :success }
                 [1 1] {:status :success :out "I am nested"}
                 [2] { :status :failure }}
              1 {[1] { :status :success :retrigger-mock-for-build-number 0 }
                 [1 1] {:status :success :out "I am nested" :retrigger-mock-for-build-number 0}
                 [2] { :status :success }}} @pipeline-state-atom))))
  (testing "that retriggering anything but a root-level step is prevented at this time"
    (let [pipeline-state-atom (atom {})
          pipeline `((some-control-flow some-step) some-successful-step)
          context { :_pipeline-state pipeline-state-atom}]
      (is (thrown? IllegalArgumentException (retrigger pipeline context 0 [2 1]))))))

(def some-pipeline
  `(
     lambdacd.steps.manualtrigger/wait-for-manual-trigger
     some-successful-step
     (lambdacd.steps.control-flow/in-parallel
       some-step)
     lambdacd.steps.manualtrigger/wait-for-manual-trigger))


(deftest if-not-killed-test
  (testing "that the body will only be executed if step is still alive"
    (let [output (async/chan 10)
          killed-ctx {:is-killed (atom true) :result-channel output}
          alive-ctx {:is-killed (atom false)}]
      (is (= {:status :success} (if-not-killed alive-ctx  {:status :success})))
      (is (= {:status :success} (if-not-killed alive-ctx  (assoc {} :status :success))))
      (is (= {:status :killed}  (if-not-killed killed-ctx   {:status :success})))))
  (testing "that the status is updated when the step was killed"
    (let [output (async/chan 10)
          killed-ctx {:is-killed (atom true) :result-channel output}]
      (if-not-killed killed-ctx  {:status :success})
      (is (= [:status :killed] (async/<!! output))))))
