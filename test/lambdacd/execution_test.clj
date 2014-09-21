(ns lambdacd.execution-test
  (:use [lambdacd.test-util])
  (:require [clojure.test :refer :all]
            [lambdacd.execution :refer :all]
            [clojure.core.async :as async]))

(defn some-step-processing-input [arg & _]
  (assoc arg :foo :baz :status :success))

(defn some-step [arg & _]
  {:foo :baz})

(defn some-other-step [arg & _]
  {:foo :baz :status :success})

(defn some-step-for-cwd [{cwd :cwd} & _]
  {:foo cwd :status :success})

(defn some-step-taking-10ms [arg & _]
  (Thread/sleep 10)
  {:foo :bar})

(defn some-successful-step [arg & _]
  {:status :success})
(defn some-failing-step [arg & _]
  {:status :failure})

(defn some-step-not-returning-status [arg & _]
  {})

(defn some-step-consuming-the-context [arg ctx]
  {:status :success :context-info (:the-info ctx)})

(defn some-step-returning-status-channel [& _]
  (let [c (async/chan 10)]
    (async/>!! c :success)
    {:status c}))

(with-private-fns [lambdacd.execution [merge-step-results]]
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
    (is (= {:outputs { [0 0] {} } :status :undefined} (execute-step some-step-not-returning-status {} {:step-id [0 0]}))))
  (testing "that the result-status can be a channel as well"
    (is (= {:outputs { [0 0] {:status :success } }:status :success} (execute-step some-step-returning-status-channel {} {:step-id [0 0]}))))
  (testing "that the context data is being passed on to the step"
    (is (= {:outputs { [0 0] {:status :success :context-info "foo"}} :status :success} (execute-step some-step-consuming-the-context {} {:step-id [0 0] :the-info "foo"})))))

(deftest context-for-steps-test
  (testing "that we can generate proper contexts for steps and keep other context info as it is"
    (is (= [[{:some-value 42 :step-id [1 0]} some-step] [{:some-value 42 :step-id [2 0]} some-step]] (context-for-steps [some-step some-step] {:some-value 42 :step-id [0 0]})))))


(deftest execute-steps-test
  (testing "that executing steps returns outputs of both steps with different step ids"
    (is (= {:outputs { [1 0] {:foo :baz :status :success} [2 0] {:foo :baz :status :success}} :status :success} (execute-steps [some-other-step some-other-step] {} { :step-id [0 0] }))))
  (testing "that a failing step prevents the succeeding steps from being executed"
    (is (= {:outputs { [1 0] {:status :success} [2 0] {:status :failure}} :status :failure} (execute-steps [some-successful-step some-failing-step some-other-step] {} { :step-id [0 0] })))))


(deftest timing-test
  (testing "that my-time more or less accurately measures the execution time of a step"
    (is (close? 2 10 (my-time (some-step-taking-10ms {}))))))


(defn counting-predicate [calls-until-true]
  (let [counter (atom 0)]
    (fn []
      (swap! counter inc)
      (>= @counter calls-until-true))))

(deftest wait-for-test
  (testing "that wait for waits until a certain predicate becomes true and waits 1000ms between"
    (is (close? 100 1000 (my-time (wait-for (counting-predicate 2))))))
  (testing "that it returns success after waiting"
    (is (= {:status :success} (wait-for (constantly true))))))
