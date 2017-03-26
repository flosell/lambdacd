(ns lambdacd.steps.control-flow-test
  (:use [lambdacd.testsupport.test-util])
  (:refer-clojure :exclude [alias])
  (:require [clojure.test :refer :all]
            [lambdacd.testsupport.matchers :refer [map-containing]]
            [lambdacd.testsupport.data :refer [some-ctx-with some-ctx]]
            [lambdacd.steps.control-flow :refer :all]
            [clojure.core.async :as async]
            [lambdacd.stepsupport.killable :as killable]
            [lambdacd.state.internal.pipeline-state-updater :as pipeline-state-updater]
            [clojure.java.io :as io]
            [lambdacd.util :as util]
            [lambdacd.testsupport.noop-pipeline-state :as noop-pipeline-state]
            [lambdacd.util.internal.map :as map-util]))

(defn some-step [arg & _]
  {:foo :baz :status :undefined})

(defn some-other-step [arg & _]
  {:foo :baz :status :success})

(defn some-step-returning-cwd [{cwd :cwd} & _]
  {:given-cwd cwd :status :success})

(defn some-step-returning-its-args [args ctx]
  {:status :success :args args})

(defn some-step-taking-100ms [arg & _]
  (Thread/sleep 100)
  {:foo :bar :status :success})

(defn some-step-taking-500ms [arg & _]
  (Thread/sleep 500)
  {:bar :baz})

(defn some-step-being-successful-after-200ms [arg & _]
  (Thread/sleep 200)
  {:successful "after a while"
   :status :success})

(defn some-successful-step [arg & _]
  {:status :success})

(defn some-killed-step [arg & _]
  {:status :killed})

(defn some-failing-step [arg & _]
  (Thread/sleep 100)
  {:status :failure})

(defn some-step-sending-failure-but-returning-success [_ {ch :result-channel}]
  (Thread/sleep 100) ; wait a bit before starting to give both steps some time
  (async/>!! ch [:status :running])
  (async/>!! ch [:status :failure])
  (Thread/sleep 20)
  {:status :success})

(defn some-step-sending-waiting-on-channel [_ {ch :result-channel}]
  (async/>!! ch [:status :running])
  (Thread/sleep 20)
  (async/>!! ch [:status :waiting])
  (Thread/sleep 100)
  {:status :success})

(defn some-step-sending-running-then-waiting-then-finished-on-channel [_ {ch :result-channel}]
  (Thread/sleep 100) ; wait a bit before starting to give both steps some time
  (Thread/sleep 10)
  (async/>!! ch [:status :waiting])
  (Thread/sleep 40)
  (async/>!! ch [:status :success])
  {:status :success})

(defn some-step-that-returns-a-global-value [& _]
  {:status :success :global {:some :value}})
(defn some-step-that-returns-another-global-value [& _]
  {:status :success :global {:some-other :value}})

(defn some-step-that-returns-42 [args ctx]
  {:status :success :the-number 42})

(defn some-failing-step-that-returns-42 [args ctx]
  {:status :failure :the-number 42})

(defn some-step-that-returns-foo [args ctx]
  {:status :success :message :foo})

(defn some-step-that-returns-bar [args ctx]
  {:status :success :message :bar})

(defn some-step-indicating-killed [was-killed]
  (fn [_ ctx]
    (loop [counter 0]
      (if (killable/killed? ctx)
        (do
          (reset! was-killed true))
        (do
          (if (< counter 1000) ;; make sure the step always eventually finishes
            (do
              (Thread/sleep 10)
              (recur (inc counter)))
            {:status :waited-too-long}))))))

(defn some-step-waiting-to-be-killed [_ ctx]
  (loop [counter 0]
    (killable/if-not-killed ctx
                                (if (< counter 100) ;; make sure the step always eventually finishes
                                  (do
                                    (Thread/sleep 100)
                                    (recur (inc counter)))
                                  {:status :waited-too-long}))))

(defn some-step-writing-into-cwd [{cwd :cwd} _]
  (let [output-file (io/file cwd "some-file")]
    (assert (.exists (io/file cwd)))
    (spit output-file "from file")
    {:status :success}))

(defn some-step-reading-from-cwd [{cwd :cwd} _]
  (let [input-file (io/file cwd "some-file")]
    (assert (.exists input-file))
    {:status :success :read-value (slurp input-file)}))

(defn some-step-that-throws-exception [_ _]
  (throw (IllegalStateException. "Dont call me!")))

(deftest in-parallel-test
  (testing "that it collects all the outputs together correctly"
    (is (map-containing {:outputs { [1 0 0] {:foo :baz :status :undefined} [2 0 0] {:foo :baz :status :undefined}} :status :undefined} ((in-parallel some-step some-step) {} (some-ctx-with :step-id [0 0])))))
  (testing "that one failing step fails the pipeline"
    (is (map-containing {:outputs { [1 0 0] {:status :success} [2 0 0] {:status :failure}} :status :failure} ((in-parallel some-successful-step some-failing-step) {} (some-ctx-with :step-id [0 0])))))
  (testing "global values are returned properly"
    (is (map-containing {:global {:some :value}} ((in-parallel some-step-that-returns-a-global-value some-successful-step) {} (some-ctx)))))
  (testing "that globals get merged"
    (is (map-containing {:global {:some :value
                                  :some-other :value}} ((in-parallel some-step-that-returns-a-global-value
                                                                     some-step-that-returns-another-global-value) {} (some-ctx)))))
  (testing "that all the result-values are merged together into a new result"
    (is (map-containing {:the-number 42 :foo :baz} ((in-parallel some-step-that-returns-42 some-other-step) {} (some-ctx)))))
  (testing "that it executes things faster than it would serially"
    (is (close? 100 100 (my-time ((in-parallel some-step-taking-100ms some-step-taking-100ms some-step-taking-100ms) {} (some-ctx))))))
  (testing "that it can inherit the result status children send over the result channel"
    (let [result-ch (async/chan 100)
          ctx (some-ctx-with :result-channel result-ch
                             :step-id [333]
                             :pipeline-state-component (noop-pipeline-state/new-no-op-pipeline-state)
                             :config {:config {:step-updates-per-sec nil}})]
      ((in-parallel some-step-sending-waiting-on-channel some-step-sending-running-then-waiting-then-finished-on-channel) {} ctx)
      (is (= [{:status :running}
              {:status :waiting}
              {:status :success}] (slurp-chan result-ch)))))
  (testing "that it doesnt immediately inherit failures on the result channel so it doesn't look like the step has failed just because a child failed"
    (let [result-ch (async/chan 100)
          ctx (some-ctx-with :result-channel result-ch :step-id [333])]
      ((in-parallel some-step-sending-failure-but-returning-success some-step-sending-running-then-waiting-then-finished-on-channel) {} ctx)
      (is (not (map-util/contains-value? :failure (slurp-chan result-ch))))))
  (testing "that it kills all children if it is killed"
    (let [is-killed (atom true)
          ctx       (some-ctx-with :is-killed is-killed
                                   :step-id [0])]
      (is (map-containing {:status :killed
                           :outputs {[1 0] {:status :killed}
                                     [2 0] {:status :killed}}} ((in-parallel some-step-waiting-to-be-killed some-step-waiting-to-be-killed) {} ctx)))))
  (testing "that retriggering doesn't retrigger both branches"
    (let [initial-pipeline-state { 0 {[1 0] {:status :success :old :one}
                                      [2 0] {:status :success :old :two}}}
          ctx (some-ctx-with :step-id [0]
                             :retriggered-build-number 0
                             :initial-pipeline-state   initial-pipeline-state)]
      (is (map-containing {:status :success
                           :outputs {[1 0] {:status :success :old :one :retrigger-mock-for-build-number 0}
                                     [2 0] {:status :success}}} ((in-parallel some-step-that-throws-exception some-successful-step) {} (assoc ctx :retriggered-step-id [2 0])))))
    (let [initial-pipeline-state { 0 {[1 0] {:status :success :old :one}
                                      [2 0] {:status :success :old :two}}}
          ctx (some-ctx-with :step-id [0]
                             :retriggered-build-number 0
                             :initial-pipeline-state   initial-pipeline-state)]
      (is (map-containing {:status :success
                           :outputs {[1 0] {:status :success}
                                     [2 0] {:status :success :old :two :retrigger-mock-for-build-number 0}}} ((in-parallel some-successful-step some-step-that-throws-exception) {} (assoc ctx :retriggered-step-id [1 0]))))))
  (testing "that retriggering the step itself works"
    (let [initial-pipeline-state { 0 {[1 0] {:status :success :old :one}
                                      [2 0] {:status :success :old :two}}}
          ctx (some-ctx-with :step-id [0]
                             :retriggered-build-number 0
                             :initial-pipeline-state   initial-pipeline-state)]
      (is (map-containing {:status :success
                           :outputs {[1 0] {:status :success}
                                     [2 0] {:status :success}}} ((in-parallel some-successful-step some-successful-step) {} (assoc ctx :retriggered-step-id [0])))))))

(deftest in-cwd-test
  (testing "that it collects all the outputs together correctly and passes cwd to steps"
    (is (map-containing {:outputs { [1 0 0] {:given-cwd "somecwd" :status :success} [2 0 0] {:foo :baz :status :success}} :status :success}
                        ((in-cwd "somecwd" some-step-returning-cwd some-other-step) {} (some-ctx-with :step-id [0 0])))))
  (testing "that all the result-values are merged together into a new result"
    (is (map-containing {:the-number 42 :foo :baz}
                        ((in-cwd "somecwd" some-step-that-returns-42 some-other-step) {} (some-ctx)))))
  (testing "global values are returned properly"
    (is (map-containing {:global {:some :value}}
                        ((in-cwd "somecwd" some-step-that-returns-a-global-value some-successful-step) {} (some-ctx))))))

(deftest run-test
  (testing "that it runs all the children and collects the results"
    (is (map-containing {:outputs { [1 0 0] {:status :success} [2 0 0] {:foo :baz :status :success}} :status :success}
                        ((run some-successful-step some-other-step) {} (some-ctx-with :step-id [0 0])))))
  (testing "that it stops after the first failure"
    (is (map-containing {:outputs { [1 0 0] {:status :success} [2 0 0] {:status :failure}} :status :failure}
                        ((run some-successful-step some-failing-step some-other-step) {} (some-ctx-with :step-id [0 0])))))
  (testing "that it pipes its args ito the first step"
    (is (map-containing {:outputs {[1 0 0] {:status :success :args {:initial :args}}}}
                        ((run some-step-returning-its-args)
                          {:initial :args} (some-ctx-with :step-id [0 0])))))
  (testing "that it pipes the initial arguments as well as the result of the previous step into subsequent steps"
    (is (map-containing {:outputs {[1 0 0] {:status :success :the-number 42}
                                   [2 0 0] {:status :success :args {:initial :args
                                                                    :status :success
                                                                    :the-number 42
                                                                    :global nil}}}}
                        ((run some-step-that-returns-42
                              some-step-returning-its-args)
                          {:initial :args} (some-ctx-with :step-id [0 0])))))
  (testing "that it kills all children if it is killed"
    (let [is-killed (atom true)
          ctx       (some-ctx-with :is-killed is-killed
                                   :step-id [0])]
      (is (map-containing {:status :killed
                           :outputs {[1 0] {:status :killed}}} ((run some-step-waiting-to-be-killed some-failing-step) {} ctx))))))

(deftest either-test
  (testing "that it succeeds whenever one step finishes successfully"
    (is (close? 100 100 (my-time ((either some-step-taking-100ms  some-step-taking-500ms) {} (some-ctx))))))
  (testing "that it returns only the results of the first successful step"
    (is (= {:status :success :foo :bar}
           ((either some-step-taking-100ms  some-step-taking-500ms) {} (some-ctx))))
    (is (= {:status :success :successful "after a while"}
           ((either some-failing-step some-step-being-successful-after-200ms) {} (some-ctx)))))
  (testing "that it fails once all children failed"
    (is (= {:status :failure}
           ((either some-failing-step some-failing-step) {} (some-ctx)))))
  (testing "that a killed child doesnt' kill all the others"
    (is (= {:status :success :foo :bar}
           ((either some-step-taking-100ms some-killed-step) {} (some-ctx)))))
  (testing "that it can inherit the result status children send over the result channel"
    (let [result-ch (async/chan 100)
          ctx (some-ctx-with :result-channel result-ch
                             :pipeline-state-component (noop-pipeline-state/new-no-op-pipeline-state)
                             :config {:config {:step-updates-per-sec nil}})]
      ((either some-step-sending-waiting-on-channel some-step-sending-running-then-waiting-then-finished-on-channel) {} ctx)
      (is (= [{:status :running}
              {:status :waiting}
              {:status :success}] (slurp-chan result-ch)))))
  (testing "that it doesn't inherit failures on the result channel so it doesn't look like the step has failed just because a child failed"
    (let [result-ch (async/chan 100)
          ctx (some-ctx-with :result-channel result-ch
                             :pipeline-state-component (noop-pipeline-state/new-no-op-pipeline-state)
                             :config {:config {:step-updates-per-sec nil}})]
      ((either some-failing-step some-step-sending-running-then-waiting-then-finished-on-channel) {} ctx)
      (is (= [{:status :running}
              {:status :waiting}
              {:status :success}] (slurp-chan result-ch)))))
  (testing "that it doesn't inherit the status of nested children"; TODO: flaky
    (let [result-ch (async/chan 100)
          ctx (some-ctx-with :result-channel result-ch
                             :pipeline-state-component (noop-pipeline-state/new-no-op-pipeline-state)
                             :config {:config {:step-updates-per-sec nil}})]
      ((either (run some-successful-step some-failing-step) some-failing-step) {} ctx)
      (is (= [{:status :running}
              {:status :success}
              {:status :running}
              {:status :failure}] (slurp-chan result-ch)))))
  (testing "that it kills all children if it was already killed in the beginning"
    (let [is-killed (atom true)
          ctx       (some-ctx-with :is-killed is-killed
                                   :step-id [0])]
      (is (map-containing {:status :killed} ((either some-step-waiting-to-be-killed some-step-waiting-to-be-killed) {} ctx)))))
  (testing "that it kills all children if it being killed later"
    (without-dead-steps
      (let [is-killed     (atom false)
            ctx           (some-ctx-with :is-killed is-killed
                                         :step-id [0])
            child-ctx     (assoc ctx :step-id [2 0])
            _             (pipeline-state-updater/start-pipeline-state-updater ctx)
            either-result (start-waiting-for ((either some-step-waiting-to-be-killed some-step-waiting-to-be-killed) {} ctx))]
        (wait-for (step-running? child-ctx))

        (reset! is-killed true)
        (is (map-containing {:status :killed} (async/<!! either-result))))))
  (testing "that it kills its children in the end"
    (let [was-killed (atom false)]
      ((either some-successful-step (some-step-indicating-killed was-killed)) {} (some-ctx))
      (Thread/sleep 50)
      (is (= true @was-killed))))
  (testing "that it doesn't kill it's parents after killing remaining children"
    (let [is-killed (atom false)
          ctx       (some-ctx-with :is-killed is-killed
                                   :step-id [0])]
      ((either some-successful-step some-step-waiting-to-be-killed) {} ctx)
      (is (= false @is-killed))))
  (testing "that retriggering retriggers all branches"
    (let [initial-pipeline-state { 0 {[1 0] {:status :killed :old :one}
                                      [2 0] {:status :success :old :two}}}
          ctx (some-ctx-with :step-id [0]
                             :retriggered-build-number 0
                             :initial-pipeline-state   initial-pipeline-state)]
      (is (map-containing {:status :success} ((either some-step-taking-100ms some-failing-step) {} (assoc ctx :retriggered-step-id [2 0])))))
    (let [initial-pipeline-state { 0 {[1 0] {:status :success :old :one}
                                      [2 0] {:status :killed :old :two}}}
          ctx (some-ctx-with :step-id [0]
                             :retriggered-build-number 0
                             :initial-pipeline-state   initial-pipeline-state)]
      (is (map-containing {:status :success} ((either some-failing-step some-step-taking-100ms ) {} (assoc ctx :retriggered-step-id [1 0])))))))

(deftest junction-test
  (testing "that it executes the success-step if the condition is a success"
    (is (map-containing {:outputs {[2 0 0] {:status :success :message :foo}}}
                        ((junction some-successful-step some-step-that-returns-foo some-step-that-returns-bar) {} (some-ctx-with :step-id [0 0])))))
  (testing "that it executes the failure-step if the condition is a failure"
    (is (map-containing {:outputs {[3 0 0] {:status :success :message :bar}}}
                        ((junction some-failing-step some-step-that-returns-foo some-step-that-returns-bar) {} (some-ctx-with :step-id [0 0])))))
  (testing "that branches receive the junctions arguments as well as the outputs from the conditional step"
    (is (map-containing {:outputs {[2 0 0] {:status :success
                                            :args {:some :arg
                                                   :the-number 42
                                                   :global nil
                                                   :status :success}}}}
                        ((junction some-step-that-returns-42
                                   some-step-returning-its-args
                                   some-step-that-returns-bar) {:some :arg} (some-ctx-with :step-id [0 0]))))
    (is (map-containing {:outputs {[3 0 0] {:status :success
                                            :args {:some :arg
                                                   :the-number 42
                                                   :global nil
                                                   :status :failure}}}}
                        ((junction some-failing-step-that-returns-42
                                   some-step-that-returns-foo
                                   some-step-returning-its-args) {:some :arg} (some-ctx-with :step-id [0 0]))))
    (is (map-containing {:outputs {[2 0 0] {:status :success
                                            :args {:some :arg
                                                   :global {:some :value}
                                                   :status :success}}}}
                        ((junction some-step-that-returns-a-global-value
                                   some-step-returning-its-args
                                   some-step-that-returns-bar) {:some :arg} (some-ctx-with :step-id [0 0])))))
  (testing "that it kills all children if it is killed"
    (let [is-killed (atom true)
          ctx       (some-ctx-with :is-killed is-killed
                                   :step-id [0])]
      (is (map-containing {:status :killed
                           :outputs {[2 0] {:status :killed}}} ((junction some-successful-step some-step-waiting-to-be-killed some-step-waiting-to-be-killed) {} ctx))))))

(deftest with-workspace-test
  (testing "that it runs all the children and collects the results"
    (is (map-containing {:outputs {[1 0 0] {:status :success}
                                   [2 0 0] {:foo :baz :status :success}} :status :success}
                        ((with-workspace some-successful-step some-other-step) {} (some-ctx-with :step-id [0 0])))))
  (testing "that it stops after the first failure"
    (is (map-containing {:outputs {[1 0 0] {:status :success}
                                   [2 0 0] {:status :failure}} :status :failure}
                        ((with-workspace some-successful-step some-failing-step some-other-step) {} (some-ctx-with :step-id [0 0])))))
  (testing "that it kills all children if it is killed"
    (let [is-killed (atom true)
          ctx       (some-ctx-with :is-killed is-killed
                                   :step-id [0])]
      (is (map-containing {:status :killed
                           :outputs {[1 0] {:status :killed}}}
                          ((with-workspace some-step-waiting-to-be-killed some-failing-step) {} ctx)))))
  (testing "that child steps receive a directory they can work in"
    (is (map-containing {:outputs {[1 0 0] {:status :success}
                                   [2 0 0] {:status :success :read-value "from file"}}}
                        ((with-workspace some-step-writing-into-cwd some-step-reading-from-cwd  ) {} (some-ctx-with :step-id [0 0])))))
  (testing "that the workspace is cleaned up after the step finishes"
    (let [result ((with-workspace some-step-returning-cwd) {} (some-ctx-with :step-id [0 0]))
          cwd    (get-in result [:outputs [1 0 0] :given-cwd])]
      (is (not (.exists (io/file cwd))))))(testing "that the workspace is cleaned up after the step finishes"
    (let [result ((with-workspace some-step-returning-cwd) {} (some-ctx-with :step-id [0 0]))
          cwd    (get-in result [:outputs [1 0 0] :given-cwd])]
      (is (not (.exists (io/file cwd))))))
  (testing "that the workspace is cleaned up after a failure"
    (let [result ((with-workspace some-step-returning-cwd some-step-that-throws-exception) {} (some-ctx-with :step-id [0 0]))
          cwd    (get-in result [:outputs [1 0 0] :given-cwd])]
      (is (not (.exists (io/file cwd)))))))
