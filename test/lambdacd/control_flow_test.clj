(ns lambdacd.control-flow-test
  (:use [lambdacd.test-util])
  (:require [clojure.test :refer :all]
            [lambdacd.control-flow :refer :all]
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

(defn some-step-returning-status-channel [& _]
  (let [c (async/chan 10)]
    (async/>!! c :success)
    {:status c}))

(deftest in-parallel-test
  (testing "that it collects all the outputs together correctly"
    (is (= {:outputs { [1 0 0] {:foo :baz} [2 0 0] {:foo :baz}} :status :undefined} ((in-parallel some-step some-step) {} [0 0]))))
  (testing "that one failing step fails the pipeline"
    (is (= {:outputs { [1 0 0] {:status :success} [2 0 0] {:status :failure}} :status :failure} ((in-parallel some-successful-step some-failing-step) {} [0 0]))))
  (testing "that it executes things faster than it would serially"
    (is (close? 3 10 (my-time ((in-parallel some-step-taking-10ms in-parallel some-step-taking-10ms in-parallel some-step-taking-10ms) {} [0 0]))))))

(deftest in-cwd-test
  (testing "that it collects all the outputs together correctly and passes cwd to steps"
    ;; FIXME: the :cwd shouldn't be in output?!!?
    (is (= {:outputs { [1 0 0] {:foo "somecwd" :status :success} [2 0 0] {:foo :baz :status :success}} :cwd "somecwd" :status :success} ((in-cwd "somecwd" some-step-for-cwd some-other-step) {} [0 0])))))

