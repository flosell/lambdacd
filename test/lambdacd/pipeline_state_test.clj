(ns lambdacd.pipeline-state-test
  (:use [lambdacd.test-util])
  (:require [clojure.test :refer :all]
            [lambdacd.pipeline-state :refer :all]))

(defn- after-update [build id newstate]
  (let [state (atom initial-pipeline-state)]
    (update { :build-number build :step-id id :_pipeline-state state} newstate)
    @state))

(defn- after-running [build id]
  (let [state (atom initial-pipeline-state)]
    (running { :build-number build :step-id id :_pipeline-state state})
    @state))

(deftest pipeline-state-test
  (testing "that after notifying about running, the pipeline state will reflect this"
    (is (= { 42 { [0] { :status :running }}} (after-running 42 [0]))))
  (testing "that a new pipeline-state will be set on update"
    (is (= { 10 { [0] { :foo :bar }}} (after-update 10 [0] {:foo :bar})))))

