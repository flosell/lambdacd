(ns lambdacd.pipeline-state-test
  (:use [lambdacd.test-util])
  (:require [clojure.test :refer :all]
            [lambdacd.pipeline-state :refer :all]))

(defn- after-update [id newstate]
  (let [state (atom initial-pipeline-state)]
    (update { :step-id id :_pipeline-state state} newstate)
    @state))

(defn- after-running [id]
  (let [state (atom initial-pipeline-state)]
    (running { :step-id id :_pipeline-state state})
    @state))

(deftest pipeline-state-test
  (testing "that after notifying about running, the pipeline state will reflect this"
    (is (= { [0] { :status :running }} (after-running [0]))))
  (testing "that a new pipeline-state will be set on update"
    (is (= { [0] { :foo :bar }} (after-update [0] {:foo :bar})))))

