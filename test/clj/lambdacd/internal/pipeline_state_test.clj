(ns lambdacd.internal.pipeline-state-test
  (:use [lambdacd.testsupport.test-util])
  (:require [clojure.test :refer :all]
            [lambdacd.internal.pipeline-state :refer :all]
            [lambdacd.util :as utils]
            [clojure.data.json :as json]
            [clj-time.core :as t]
            [lambdacd.testsupport.test-util :as tu]
            [lambdacd.testsupport.data :refer [some-ctx-with]]
            [clojure.data :as d]
            [clojure.java.io :as io]
            [clojure.core.async :as async]))

(defn- after-update [build id newstate]
  (let [state (atom clean-pipeline-state)]
    (update build id newstate nil state)
    @state))

(deftest general-pipeline-state-test
  (testing "that the next buildnumber is the highest build-number currently in the pipeline-state"
    (is (= 5 (next-build-number {:_pipeline-state (atom { 3 {} 4 {} 1 {}})})))
    (is (= 1 (next-build-number {:_pipeline-state (atom clean-pipeline-state)}))))
  (testing "that a new pipeline-state will be set on update"
    (is (= { 10 { [0] { :foo :bar }}} (tu/without-ts (after-update 10 [0] {:foo :bar})))))
  (testing "that update will not loose keys that are not in the new map" ; e.g. to make sure values that are sent on the result-channel are not lost if they don't appear in the final result-map
    (is (= { 10 { [0] { :foo :bar :bar :baz }}}
           (let [state (atom clean-pipeline-state)]
             (update 10 [0] {:foo :bar} nil state)
             (update 10 [0] {:bar :baz} nil state)
             (tu/without-ts @state)))))
  (testing "that update will set a first-updated-at and most-recent-update-at timestamp"
    (let [first-update-timestamp (t/minus (t/now) (t/minutes 1))
          last-updated-timestamp (t/now)
          state (atom clean-pipeline-state)]
      (t/do-at first-update-timestamp (update 10 [0] {:foo :bar} nil state))
      (t/do-at last-updated-timestamp (update 10 [0] {:foo :baz} nil state))
      (is (= {10 {[0] {:foo :baz :most-recent-update-at last-updated-timestamp :first-updated-at first-update-timestamp }}} @state))))
  (testing "that updating will save the current state to the file-system"
    (let [home-dir (utils/create-temp-dir)
          step-result { :foo :bar }]
      (t/do-at (t/epoch) (update 10 [0] step-result home-dir (atom {})))
      (is (= [{ "step-id" "0"
               "step-result" {"foo" "bar"
                              "most-recent-update-at" "1970-01-01T00:00:00.000Z"
                              "first-updated-at" "1970-01-01T00:00:00.000Z"}}] (json/read-str (slurp (str home-dir "/build-10/pipeline-state.json"))))))))

(deftest initialize-pipeline-persistence-test
  (testing "that we tap into a pipelines step-results-channel and update the pipeline state with its information"
    (let [state (atom {})
          step-results-channel (async/chan 10)
          context (some-ctx-with :step-results-channel step-results-channel)]
      (async/>!! step-results-channel {:build-number 1 :step-id [1 2] :step-result {:status :running}})
      (async/>!! step-results-channel {:build-number 2 :step-id [1 2] :step-result {:status :success}})
      (async/>!! step-results-channel {:build-number 1 :step-id [1 2] :step-result {:status :running :foo :bar}})

      (async/close! step-results-channel)
      (async/<!! (start-pipeline-state-updater state context))
      (is (= {1 { [1 2] {:status :running :foo :bar}}
              2 { [1 2] {:status :success}}} (tu/without-ts @state))))))

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

(deftest notify-when-most-recent-build-running-test
  (testing "that we are being notified when the first step of the pipeline is finished"
    (let [pipeline-state (atom {0 {}})
          call-counter (atom 0)
          callback (fn [& _] (swap! call-counter inc))]
      (notify-when-no-first-step-is-active { :_pipeline-state pipeline-state} callback)
      (is (= 0 @call-counter))
      (update 0 [1] {:status :running} nil pipeline-state)
      (is (= 0 @call-counter))
      (update 0 [1] {:status :success} nil pipeline-state)
      (is (= 1 @call-counter))
      (update 0 [2] {:status :success} nil pipeline-state)
      (is (= 1 @call-counter))
      (update 1 [1] {:status :waiting} nil pipeline-state)
      (is (= 1 @call-counter))
      (update 1 [1] {:status :running} nil pipeline-state)
      (is (= 1 @call-counter))
      (update 1 [1] {:status :failure} nil pipeline-state)
      (is (= 2 @call-counter))
      (update 1 [1] {:status :failure} nil pipeline-state)
      (is (= 2 @call-counter))
      (update 2 [2] {:status :ok :retrigger-mock-for-build-number 1 } nil pipeline-state)
      (is (= 2 @call-counter))
      (update 3 [2] {:status :ok } nil pipeline-state)
      (is (= 2 @call-counter))))
  (testing "that we are not notified if there is already a build waiting"
    (let [pipeline-state (atom {0 { [1] {:status :waiting}}
                                1 { [1] {:status :waiting}}})
          call-counter (atom 0)
          callback (fn [& _] (swap! call-counter inc))]
      (notify-when-no-first-step-is-active { :_pipeline-state pipeline-state} callback)
      (is (= 0 @call-counter))
      (update 1 [1] {:status :success} nil pipeline-state)
      (is (= 0 @call-counter)))))
