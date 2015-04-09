(ns lambdacd.steps.support-test
  (:require [clojure.test :refer :all]
            [lambdacd.steps.support :refer :all]))

(defn some-step [args ctx]
  {:status :success :foo :bar})

(defn some-other-step [args ctx]
  {:status :success :foo :baz})

(defn some-failling-step [args ctx]
  {:status :failure})
(defn step-that-should-never-be-called [args ctx]
  (throw (IllegalStateException. "do not call me!")))

(deftest chain-test
  (testing "that we can just call a single step"
    (is (= {:status :success :foo :bar} (chain (some-step {} {})))))
  (testing "that the results of two steps get merged"
    (is (= {:status :success :foo :baz} (chain
                                         (some-step {} {})
                                         (some-other-step {} {})))))
  (testing "that the result of a step is passed on to the next step"
    (is (= {:status :success :foo :baz} (chain
                                          (some-step {} {})
                                          (some-other-step {} {})))))
  (testing "that a failing step stops the execution"
    (is (= {:status :failure :foo :bar} (chain
                                          (some-step {} {})
                                          (some-failling-step {} {})
                                          (step-that-should-never-be-called {} {}))))))