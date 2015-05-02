(ns lambdacd.steps.control-flow-test
  (:use [lambdacd.testsupport.test-util])
  (:require [clojure.test :refer :all]
            [lambdacd.steps.control-flow :refer :all]))

(defn some-step [arg & _]
  {:foo :baz})

(defn some-other-step [arg & _]
  {:foo :baz :status :success})

(defn some-step-for-cwd [{cwd :cwd} & _]
  {:foo cwd :status :success})

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
(defn some-failing-step [arg & _]
  {:status :failure})


(deftest in-parallel-test
  (testing "that it collects all the outputs together correctly"
    (is (= {:outputs { [1 0 0] {:foo :baz :status :undefined} [2 0 0] {:foo :baz :status :undefined}} :status :undefined} ((in-parallel some-step some-step) {} { :step-id [0 0] }))))
  (testing "that one failing step fails the pipeline"
    (is (= {:outputs { [1 0 0] {:status :success} [2 0 0] {:status :failure}} :status :failure} ((in-parallel some-successful-step some-failing-step) {} {:step-id [0 0]}))))
  (testing "that it executes things faster than it would serially"
    (is (close? 100 100 (my-time ((in-parallel some-step-taking-100ms some-step-taking-100ms some-step-taking-100ms) {} {:step-id [0 0]}))))))

(deftest in-cwd-test
  (testing "that it collects all the outputs together correctly and passes cwd to steps"
    (is (= {:outputs { [1 0 0] {:foo "somecwd" :status :success} [2 0 0] {:foo :baz :status :success}} :status :success} ((in-cwd "somecwd" some-step-for-cwd some-other-step) {} {:step-id [0 0]})))))

(deftest either-test
  (testing "that it succeeds whenever one step finishes successfully"
    (is (close? 100 100 (my-time ((either some-step-taking-100ms  some-step-taking-500ms) {} {:step-id [0 0]})))))
  (testing "that it returns only the results of the first successful step"
    (is (= {:status :success :foo :bar}
           ((either some-step-taking-100ms  some-step-taking-500ms) {} { :step-id [0 0] })))
    (is (= {:status :success :successful "after a while"}
           ((either some-failing-step some-step-being-successful-after-200ms) {} { :step-id [0 0] }))))
  (testing "that it fails once all children failed"
    (is (= { :status :failure }
           ((either some-failing-step some-failing-step) {} { :step-id [0 0] })))))
