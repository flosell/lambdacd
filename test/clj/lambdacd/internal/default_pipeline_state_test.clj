(ns lambdacd.internal.default-pipeline-state-test
  (:use [lambdacd.testsupport.test-util])
  (:require [clojure.test :refer :all]
            [lambdacd.internal.default-pipeline-state :refer :all]
            [lambdacd.internal.pipeline-state :refer [next-build-number] :as pipeline-state-record]
            [lambdacd.util :as utils]
            [clojure.data.json :as json]
            [clj-time.core :as t]
            [lambdacd.testsupport.test-util :as tu]
            [lambdacd.testsupport.data :refer [some-ctx-with some-ctx]]
            [clojure.java.io :as io]))

(def no-home-dir nil)

(defn- after-update [build id newstate]
  (let [state (->DefaultPipelineState (atom clean-pipeline-state) no-home-dir)]
    (pipeline-state-record/update state build id newstate)
    (pipeline-state-record/get-all state)))

(deftest general-pipeline-state-test
  (testing "that the next buildnumber is the highest build-number currently in the pipeline-state"
    (is (= 5 (next-build-number (->DefaultPipelineState (atom { 3 {} 4 {} 1 {}}) no-home-dir))))
    (is (= 1 (next-build-number (->DefaultPipelineState (atom clean-pipeline-state) no-home-dir)))))
  (testing "that a new pipeline-state will be set on update"
    (is (= { 10 { [0] { :foo :bar }}} (tu/without-ts (after-update 10 [0] {:foo :bar})))))
  (testing "that update will not loose keys that are not in the new map" ; e.g. to make sure values that are sent on the result-channel are not lost if they don't appear in the final result-map
    (is (= {10 {[0] {:foo :bar :bar :baz}}}
           (let [state (->DefaultPipelineState (atom clean-pipeline-state) no-home-dir)]
             (pipeline-state-record/update state 10 [0] {:foo :bar})
             (pipeline-state-record/update state 10 [0] {:bar :baz})
             (tu/without-ts (pipeline-state-record/get-all state))))))
  (testing "that update will set a first-updated-at and most-recent-update-at timestamp"
    (let [first-update-timestamp (t/minus (t/now) (t/minutes 1))
          last-updated-timestamp (t/now)
          state                  (->DefaultPipelineState (atom clean-pipeline-state) no-home-dir)]
      (t/do-at first-update-timestamp (pipeline-state-record/update state 10 [0] {:foo :bar}))
      (t/do-at last-updated-timestamp (pipeline-state-record/update state 10 [0] {:foo :baz}))
      (is (= {10 {[0] {:foo :baz :most-recent-update-at last-updated-timestamp :first-updated-at first-update-timestamp}}}
             (pipeline-state-record/get-all state)))))
  (testing "that updating will save the current state to the file-system"
    (let [home-dir    (utils/create-temp-dir)
          step-result {:foo :bar}
          state       (->DefaultPipelineState (atom clean-pipeline-state) home-dir)]
      (t/do-at (t/epoch) (pipeline-state-record/update state 10 [0] step-result))
      (is (= [{"step-id"     "0"
               "step-result" {"foo"                   "bar"
                              "most-recent-update-at" "1970-01-01T00:00:00.000Z"
                              "first-updated-at"      "1970-01-01T00:00:00.000Z"}}]
             (json/read-str (slurp (str home-dir "/build-10/pipeline-state.json"))))))))

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


