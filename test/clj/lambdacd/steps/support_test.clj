(ns lambdacd.steps.support-test
  (:require [clojure.test :refer :all]
            [lambdacd.steps.support :refer :all]))

(defn some-step [args ctx]
  {:status :success :foo :bar})

(defn some-other-step [args ctx]
  {:status :success :foo :baz})

(defn some-failling-step [args ctx]
  {:status :failure})

(defn some-step-that-returns-a-value [args ctx]
  {:v 42 :status :success})

(defn some-step-returning-a-global [args ctx]
  {:global {:g 42} :status :success})
(defn some-step-returning-a-different-global [args ctx]
  {:global {:v 21} :status :success})
(defn some-step-returning-a-global-argument-passed-in [args ctx]
  {:the-global-arg (:g (:global args)) :status :success})

(defn some-step-returning-an-argument-passed-in [args ctx]
  {:status :success :the-arg (:v args)})

(defn some-step-returning-the-context-passed-in [args ctx]
  {:status :success :the-ctx-1 ctx})

(defn some-other-step-returning-the-context-passed-in [args ctx]
  {:status :success :the-ctx-2 ctx})

(defn some-step-saying-hello [args ctx]
  {:status :success :out "hello"})
(defn some-step-saying-world [args ctx]
  {:status :success :out "world"})


(defn step-that-should-never-be-called [args ctx]
  (throw (IllegalStateException. "do not call me!")))

(deftest execute-until-failure-test
  (testing "that the input argument is passed to the first step"
    (is (= {:status :success :the-arg 42} (execute-until-failure {:v 42} {}
                                                                 [some-step-returning-an-argument-passed-in]))))
  (testing "that the results of two steps get merged"
    (is (= {:status :success :foo :baz} (execute-until-failure {} {}
                                                               [some-step some-other-step]))))
  (testing "that a failing step stops the execution"
    (is (= {:status :failure :foo :bar} (execute-until-failure {} {}
                                                               [some-step
                                                                some-failling-step
                                                                step-that-should-never-be-called]))))
  (testing "that the results of the first step are the input for the next step"
    (is (= {:status :success :the-arg 42 :v 42} (execute-until-failure {} {}
                                                                        [some-step-that-returns-a-value
                                                                         some-step-returning-an-argument-passed-in]))))
  (testing "that global values are being kept over all steps"
    (is (= {:status :success
            :the-global-arg 42
            :global {:g 42 :v 21}} (execute-until-failure {} {} [some-step-returning-a-global
                                                     some-step-returning-a-different-global
                                                     some-step-returning-a-global-argument-passed-in]))))
  (testing "that overlapping string-outputs get concatenated"
    (is (= {:status :success
            :out "hello\nworld"} (execute-until-failure {} {} [some-step-saying-hello
                                                               some-step-saying-world]))))
  (testing "that the context is passed to all the steps"
    (is (= {:status :success :the-ctx-1 {:v 42} :the-ctx-2 {:v 42}} (execute-until-failure {} {:v 42}
                                                                   [some-step-returning-the-context-passed-in
                                                                    some-other-step-returning-the-context-passed-in])))))

(deftest chain-test
  (testing "that we can just call a single step"
    (is (= {:status :success :foo :bar} (chain {} {} (some-step)))))
  (testing "that the results of two steps get merged"
    (is (= {:status :success :foo :baz} (chain {} {}
                                         (some-step)
                                         (some-other-step)))))
  (testing "that a failing step stops the execution"
    (is (= {:status :failure :foo :bar} (chain {} {}
                                          (some-step )
                                          (some-failling-step)
                                          (step-that-should-never-be-called)))))
  (testing "that a given argument is passed on to the step"
    (is (= {:status :success :the-arg 42} (chain {:v 42} {}
                                               (some-step-returning-an-argument-passed-in)))))
  (testing "that a given context is passed on to the step"
    (is (= {:status :success :the-ctx-1 {:v 42}} (chain {} {:v 42}
                                                 (some-step-returning-the-context-passed-in))))))