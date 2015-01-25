(ns lambdacd.pipeline-state-test
  (:use [lambdacd.test-util])
  (:require [clojure.test :refer :all]
            [lambdacd.pipeline-state :refer :all]
            [lambdacd.util :as utils]
            [clojure.data.json :as json]
            [clojure.java.io :as io]))

(defn- after-update [build id newstate]
  (let [state (atom clean-pipeline-state)]
    (update { :build-number build :step-id id :_pipeline-state state} newstate)
    @state))

(defn- after-running [build id]
  (let [state (atom clean-pipeline-state)]
    (running { :build-number build :step-id id :_pipeline-state state})
    @state))

(deftest pipeline-state-test
  (testing "that the current build-number is the highest build-number currently in the pipeline-state"
    (is (= 4 (current-build-number {:_pipeline-state (atom { 3 {} 4 {} 1 {}})})))
    (is (= 0 (current-build-number {:_pipeline-state (atom clean-pipeline-state)}))))
  (testing "that after notifying about running, the pipeline state will reflect this"
    (is (= { 42 { [0] { :status :running }}} (after-running 42 [0]))))
  (testing "that a new pipeline-state will be set on update"
    (is (= { 10 { [0] { :foo :bar }}} (after-update 10 [0] {:foo :bar}))))
  (testing "that that update will not loose keys that are not in the new map" ; e.g. to make sure values that are sent on the result-channel are not lost if they don't appear in the final result-map
    (is (= { 10 { [0] { :foo :bar :bar :baz }}}
           (let [state (atom clean-pipeline-state)]
             (update { :build-number 10 :step-id [0] :_pipeline-state state} {:foo :bar})
             (update { :build-number 10 :step-id [0] :_pipeline-state state} {:bar :baz})
             @state
             ))))
  (testing "that updating will save the current state to the file-system"
    (let [home-dir (utils/create-temp-dir)
          config { :home-dir home-dir }
          step-result { :foo :bar }
          ctx { :build-number 10  :step-id [0] :config config :_pipeline-state (atom nil)}]
      (update ctx step-result)
      (is (= [{ "step-id" "0" "step-result" { "foo" "bar" }}] (json/read-str (slurp (str home-dir "/build-10/pipeline-state.json"))))))))

(defn- write-pipeline-state [home-dir build-number state]
  (let [dir (str home-dir "/" "build-" build-number)
        path (str dir "/pipeline-state.json")]
    (.mkdirs (io/file dir))
    (utils/write-as-json path state)))

(deftest initial-pipeline-state-test
  (testing "that with a clean home-directory, the initial pipeline-state is clean as well"
    (let [home-dir (utils/create-temp-dir)
          config {:home-dir home-dir}]
      (is (= clean-pipeline-state (initial-pipeline-state config)))))
  (testing "that it reads the state from disk correctly"
    (let [home-dir (utils/create-temp-dir)
          config {:home-dir home-dir}]
      (write-pipeline-state home-dir 1 [{:step-id "0" :step-result {:foo "bar" }}])
      (write-pipeline-state home-dir 2 [{:step-id "1-2" :step-result {:bar "baz" }}])
      (is (= {1 { [0]   {:foo "bar"}}
              2 { [1 2] {:bar "baz"}}} (initial-pipeline-state config))))))


(deftest history-test
  (testing "that it converts the internal pipeline-state into a more readable history-representation"
    (is (= [{ :build-number 5
             :status :running}
            { :build-number 6
             :status :ok}
            { :build-number 7
             :status :failure}
            { :build-number 8
             :status :waiting}
            { :build-number 9
              :status :unknown}
            ] (history-for { 5 { [0] { :status :ok } [1] { :status :running}}
                             6 { [0] { :status :ok } }
                             7 { [0 2] { :status :running} [0 1] { :status :failure}}
                             8 { [0] { :status :waiting }}
                             9 { [0] { :no :status }}})))))

(deftest most-recent-build-test
  (testing "that it returns the most recent build number in the pipeline-state"
    (is (= 9 (most-recent-build-number-in { 5 { }
                                            6 {  }
                                            9 { }})))))

(deftest last-step-result-test
  (testing "that we can access the last step result for a particular step"
    (is (= {:status :success :foo :bar}
           (last-step-result
             {:step-id [0 2]
              :_pipeline-state
              (atom
                {9 { [0 2] { :status :running} [0 1] { :status :failure}}
                 8 { [0 2] {:status :success :foo :bar} [0 1] { :status :failure}}})})))))