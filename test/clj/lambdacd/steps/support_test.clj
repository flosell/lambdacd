(ns lambdacd.steps.support-test
  (:require [clojure.test :refer :all]
            [lambdacd.steps.support :refer :all]))

(defn some-step [args ctx]
  {:status :success :foo :bar})

(defn some-other-step [args ctx]
  {:status :success :foo :baz})

(defn some-failling-step [args ctx]
  {:status :failure})

(defn some-step-returning-an-argument-passed-in [args ctx]
  {:status :success :the-arg (:arg args)})

(defn some-step-returning-the-context-passed-in [args ctx]
  {:status :success :the-ctx-1 ctx})
(defn some-other-step-returning-the-context-passed-in [args ctx]
  {:status :success :the-ctx-2 ctx})


(defn step-that-should-never-be-called [args ctx]
  (throw (IllegalStateException. "do not call me!")))

(deftest execute-until-failure-test
  (testing "that the input argument is passed to the first step"
    (is (= {:status :success :the-arg 42} (execute-until-failure {:arg 42} {}
                                                                 [some-step-returning-an-argument-passed-in]))))
  (testing "that the results of two steps get merged"
    (is (= {:status :success :foo :baz} (execute-until-failure {} {}
                                                               [some-step some-other-step]))))
  (testing "that a failing step stops the execution"
    (is (= {:status :failure :foo :bar} (execute-until-failure {} {}
                                                               [some-step
                                                                some-failling-step
                                                                step-that-should-never-be-called]))))
  (testing "that the context is passed to all the steps"
    (is (= {:status :success :the-ctx-1 {:v 42} :the-ctx-2 {:v 42}} (execute-until-failure {} {:v 42}
                                                                   [some-step-returning-the-context-passed-in
                                                                    some-other-step-returning-the-context-passed-in])))))

(deftest chain-test
  (testing "that we can just call a single step"
    (is (= {:status :success :foo :bar} (chain (some-step {} {})))))
  (testing "that the results of two steps get merged"
    (is (= {:status :success :foo :baz} (chain
                                         (some-step {} {})
                                         (some-other-step {} {})))))
  (testing "that a failing step stops the execution"
    (is (= {:status :failure :foo :bar} (chain
                                          (some-step {} {})
                                          (some-failling-step {} {})
                                          (step-that-should-never-be-called {} {}))))))