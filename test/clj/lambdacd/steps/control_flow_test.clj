(ns lambdacd.steps.control-flow-test
  (:use [lambdacd.testsupport.test-util])
  (:require [clojure.test :refer :all]
            [lambdacd.steps.control-flow :as control-flow]
            [lambdacd.testsupport.matchers :refer [map-containing]]
            [lambdacd.testsupport.data :refer [some-ctx-with some-ctx]]
            [lambdacd.steps.control-flow :refer :all]
            [clojure.core.async :as async]))

(defn some-step [arg & _]
  {:foo :baz :status :undefined})

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

(defn some-step-sending-waiting-on-channel [_ {ch :result-channel}]
  (async/>!! ch [:status :running])
  (Thread/sleep 20)
  (async/>!! ch [:status :waiting])
  (Thread/sleep 100)
  {:status :success})

(defn some-step-sending-running-then-waiting-then-finished-on-channel [_ {ch :result-channel}]
  (Thread/sleep 10)
  (async/>!! ch [:status :waiting])
  (Thread/sleep 40)
  (async/>!! ch [:status :success])
  {:status :success})

(defn some-step-that-returns-a-global-value [& _]
  {:status :success :global {:some :value}})

(defn some-step-that-returns-42 [args ctx]
  {:status :success :the-number 42})

(deftest in-parallel-test
  (testing "that it collects all the outputs together correctly"
    (is (map-containing {:outputs { [1 0 0] {:foo :baz :status :undefined} [2 0 0] {:foo :baz :status :undefined}} :status :undefined} ((in-parallel some-step some-step) {} (some-ctx-with :step-id [0 0])))))
  (testing "that one failing step fails the pipeline"
    (is (map-containing {:outputs { [1 0 0] {:status :success} [2 0 0] {:status :failure}} :status :failure} ((in-parallel some-successful-step some-failing-step) {} (some-ctx-with :step-id [0 0])))))
  (testing "global values are returned properly"
    (is (map-containing {:global {:some :value}} ((in-parallel some-step-that-returns-a-global-value some-successful-step) {} (some-ctx)))))
  (testing "that all the result-values are merged together into a new result"
    (is (map-containing {:the-number 42 :foo :baz} ((in-parallel some-step-that-returns-42 some-other-step) {} (some-ctx)))))
  (testing "that it executes things faster than it would serially"
    (is (close? 100 100 (my-time ((in-parallel some-step-taking-100ms some-step-taking-100ms some-step-taking-100ms) {} (some-ctx))))))
  (testing "that it can inherit the result status children send over the result channel"
    (let [result-ch (async/chan 100)
          ctx (some-ctx-with :result-channel result-ch)]
      ((in-parallel some-step-sending-waiting-on-channel some-step-sending-running-then-waiting-then-finished-on-channel) {} ctx)
      (is (= [[:status :running]
              [:status :running]
              [:status :waiting]
              [:status :success]] (slurp-chan result-ch))))))

(deftest in-cwd-test
  (testing "that it collects all the outputs together correctly and passes cwd to steps"
    (is (map-containing {:outputs { [1 0 0] {:foo "somecwd" :status :success} [2 0 0] {:foo :baz :status :success}} :status :success}
                        ((in-cwd "somecwd" some-step-for-cwd some-other-step) {} (some-ctx-with :step-id [0 0])))))
  (testing "that all the result-values are merged together into a new result"
    (is (map-containing {:the-number 42 :foo :baz}
                        ((in-cwd "somecwd" some-step-that-returns-42 some-other-step) {} (some-ctx)))))
  (testing "global values are returned properly"
    (is (map-containing {:global {:some :value}}
                        ((in-cwd "somecwd" some-step-that-returns-a-global-value some-successful-step) {} (some-ctx))))))

(deftest do-test
  (testing "that it runs all the children and collects the results"
    (is (map-containing {:outputs { [1 0 0] {:status :success} [2 0 0] {:foo :baz :status :success}} :status :success}
                        ((run some-successful-step some-other-step) {} (some-ctx-with :step-id [0 0])))))
  (testing "that it stops after the first failure"
    (is (map-containing {:outputs { [1 0 0] {:status :success} [2 0 0] {:status :failure}} :status :failure}
                        ((run some-successful-step some-failing-step some-other-step) {} (some-ctx-with :step-id [0 0]))))))

(deftest either-test
  (testing "that it succeeds whenever one step finishes successfully"
    (is (close? 100 100 (my-time ((either some-step-taking-100ms  some-step-taking-500ms) {} (some-ctx))))))
  (testing "that it returns only the results of the first successful step"
    (is (= {:status :success :foo :bar}
           ((either some-step-taking-100ms  some-step-taking-500ms) {} (some-ctx))))
    (is (= {:status :success :successful "after a while"}
           ((either some-failing-step some-step-being-successful-after-200ms) {} (some-ctx)))))
  (testing "that it fails once all children failed"
    (is (= { :status :failure }
           ((either some-failing-step some-failing-step) {} (some-ctx)))))
  (testing "that it can inherit the result status children send over the result channel"
    (let [result-ch (async/chan 100)
          ctx (some-ctx-with :result-channel result-ch)]
      ((either some-step-sending-waiting-on-channel some-step-sending-running-then-waiting-then-finished-on-channel) {} ctx)
      (is (= [[:status :running]
              [:status :running]
              [:status :waiting]
              [:status :success]] (slurp-chan result-ch))))))
