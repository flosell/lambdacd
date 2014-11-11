(ns lambdacd.pipeline-state-test
  (:use [lambdacd.test-util])
  (:require [clojure.test :refer :all]
            [lambdacd.pipeline-state :refer :all]
            [lambdacd.util :as utils]
            [clojure.data.json :as json]))

(defn- after-update [build id newstate]
  (let [state (atom initial-pipeline-state)]
    (update { :build-number build :step-id id :_pipeline-state state} newstate)
    @state))

(defn- after-running [build id]
  (let [state (atom initial-pipeline-state)]
    (running { :build-number build :step-id id :_pipeline-state state})
    @state))

(deftest pipeline-state-test
  (testing "that the current build-number is the highest build-number currently in the pipeline-state"
    (is (= 4 (current-build-number {:_pipeline-state (atom { 3 {} 4 {} 1 {}})})))
    (is (= 0 (current-build-number {:_pipeline-state (atom initial-pipeline-state)}))))
  (testing "that after notifying about running, the pipeline state will reflect this"
    (is (= { 42 { [0] { :status :running }}} (after-running 42 [0]))))
  (testing "that a new pipeline-state will be set on update"
    (is (= { 10 { [0] { :foo :bar }}} (after-update 10 [0] {:foo :bar}))))
  (testing "that updating will save the current state to the file-system"
    (let [home-dir (utils/create-temp-dir)
          config { :home-dir home-dir }
          step-result { :foo :bar }
          ctx { :build-number 10  :step-id [0] :config config :_pipeline-state (atom nil)}]
      (update ctx step-result)
      (is (= [{ "step-id" "0" "step-result" { "foo" "bar" }}] (json/read-str (slurp (str home-dir "/build-10/pipeline-state.json"))))))))

