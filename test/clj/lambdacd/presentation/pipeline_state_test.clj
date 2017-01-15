(ns lambdacd.presentation.pipeline-state-test
  (:use [lambdacd.testsupport.test-util])
  (:require [clojure.test :refer :all]
            [lambdacd.presentation.pipeline-state :refer :all]
            [lambdacd.testsupport.data :refer [some-ctx-with]]
            [clj-time.core :as t]
            [lambdacd.state.protocols :as protocols]))

(def start-time (t/now))
(def before-start-time (t/minus start-time (t/seconds 10)))
(def stop-time (t/plus start-time (t/seconds 10)))
(def after-stop-time (t/plus stop-time (t/seconds 10)))
(def long-before-start-time (t/minus start-time (t/hours 10)))
(def long-after-stop-time (t/plus stop-time (t/hours 10)))

(def after-ten-sec (t/plus start-time (t/seconds 10)))
(def after-twenty-sec (t/plus start-time (t/seconds 20)))
(def after-thirty-sec (t/plus start-time (t/seconds 30)))
(def after-one-minute-and-30-sec (t/plus start-time (t/minutes 1) (t/seconds 30)))
(def after-one-minute-and-40-sec (t/plus start-time (t/minutes 1) (t/seconds 40)))

(defrecord in-memory-pipeline-state [state build-metadata]
  protocols/QueryAllBuildNumbersSource
  (all-build-numbers [self]
    (keys state))
  protocols/QueryStepResultsSource
  (get-step-results [self build-number]
    (get state build-number))
  protocols/BuildMetadataSource
  (get-build-metadata [self build-number]
    (get build-metadata build-number)))

(defn ctx-with-state
  ([state build-metadata]
   (some-ctx-with :pipeline-state-component (->in-memory-pipeline-state state build-metadata)))
  ([state]
   (ctx-with-state state {})))

(deftest history-for-ctx-test
  (testing "that we can aggregate the pipeline state into a nice history representation"
    (is (= [{:build-number          8
             :status                :waiting
             :most-recent-update-at stop-time
             :first-updated-at      start-time
             :retriggered           nil
             :duration-in-sec       10
             :build-metadata {:some :metadata}}
            {:build-number          9
             :status                :running
             :most-recent-update-at stop-time
             :first-updated-at      start-time
             :retriggered           nil
             :duration-in-sec       10
             :build-metadata {}}]
           (history-for (ctx-with-state {8 {'(0) {:status :waiting :most-recent-update-at stop-time :first-updated-at start-time}}
                                         9 {'(0) {:status :running :most-recent-update-at stop-time :first-updated-at start-time}}}
                                        {8 {:some :metadata}
                                         9 {}})))))
  (testing "that the timestamps are accumulated correctly"
    (testing "that the earliest start time is the start time of the pipeline"
      (is (= before-start-time (:first-updated-at (first
                                                    (history-for (ctx-with-state {7 {'(2) {:status :success :most-recent-update-at stop-time :first-updated-at start-time}
                                                                                     '(1) {:status :success :most-recent-update-at stop-time :first-updated-at before-start-time}}})))))))
    (testing "that retriggered steps are ignored when searching the earliest start time"
      (is (= start-time (:first-updated-at (first
                                             (history-for (ctx-with-state {7 {'(2) {:status :success :most-recent-update-at stop-time :first-updated-at start-time}
                                                                              '(1) {:status :success :most-recent-update-at stop-time :first-updated-at before-start-time :retrigger-mock-for-build-number 42}}})))))))
    (testing "that the most recent update will be the pipelines most recent update"
      (is (= after-stop-time (:most-recent-update-at (first
                                                       (history-for (ctx-with-state {5 {'(0) {:status :success :most-recent-update-at stop-time :first-updated-at start-time}
                                                                                        '(1) {:status :success :most-recent-update-at after-stop-time :first-updated-at start-time}}}))))))))
  (testing "that the build-status is accumulated correctly"
    (testing "that a pipeline will be a failure once there is a failed step"
      (is (= :failure (:status (first
                                 (history-for (ctx-with-state {7 {'(1) {:status :success :most-recent-update-at stop-time :first-updated-at start-time}
                                                                  '(2) {:status :failure :most-recent-update-at stop-time :first-updated-at start-time}}}))))))))
  (testing "that we detect retriggered steps"
    (testing "that a pipeline will be treated as retriggered if the first step has a retrigger-mock"
      (is (= 3 (:retriggered (first
                               (history-for (ctx-with-state {7 {'(2) {:status :success :most-recent-update-at stop-time :first-updated-at start-time}
                                                                '(1) {:status :success :most-recent-update-at stop-time :first-updated-at before-start-time :retrigger-mock-for-build-number 3}}})))))))))

(deftest history-test-legacy
  (testing "that we can aggregate the pipeline state into a nice history representation"
    (is (= [{:build-number 8
             :status :waiting
             :most-recent-update-at stop-time
             :first-updated-at start-time
             :retriggered nil
             :duration-in-sec 10
             :build-metadata {}}
            {:build-number 9
             :status :running
             :most-recent-update-at stop-time
             :first-updated-at start-time
             :retriggered nil
             :duration-in-sec 10
             :build-metadata {}}]
           (history-for {8  {'(0)   { :status :waiting :most-recent-update-at stop-time :first-updated-at start-time}}
                         9  {'(0)   { :status :running :most-recent-update-at stop-time :first-updated-at start-time}}}))))
  (testing "that the timestamps are accumulated correctly"
    (testing "that the earliest start time is the start time of the pipeline"
      (is (= before-start-time (:first-updated-at (first
                                                    (history-for {7 {'(2) {:status :success :most-recent-update-at stop-time :first-updated-at start-time}
                                                                     '(1) {:status :success :most-recent-update-at stop-time :first-updated-at before-start-time}}}))))))
    (testing "that retriggered steps are ignored when searching the earliest start time"
      (is (= start-time (:first-updated-at (first
                                             (history-for {7 {'(2) {:status :success :most-recent-update-at stop-time :first-updated-at start-time}
                                                              '(1) {:status :success :most-recent-update-at stop-time :first-updated-at before-start-time :retrigger-mock-for-build-number 42}}}))))))
    (testing "that the most recent update will be the pipelines most recent update"
      (is (= after-stop-time (:most-recent-update-at (first
                                                       (history-for {5 {'(0) {:status :success :most-recent-update-at stop-time :first-updated-at start-time}
                                                                        '(1) {:status :success :most-recent-update-at after-stop-time :first-updated-at start-time}}})))))))
  (testing "that the build-status is accumulated correctly"
    (testing "that a pipeline will be a failure once there is a failed step"
      (is (= :failure (:status (first
                                 (history-for {7 {'(1) {:status :success :most-recent-update-at stop-time :first-updated-at start-time}
                                                  '(2) {:status :failure :most-recent-update-at stop-time :first-updated-at start-time}}})))))))
  (testing "that we detect retriggered steps"
    (testing "that a pipeline will be treated as retriggered if the first step has a retrigger-mock"
      (is (= 3 (:retriggered (first
                               (history-for {7 {'(2) {:status :success :most-recent-update-at stop-time :first-updated-at start-time}
                                                '(1) {:status :success :most-recent-update-at stop-time :first-updated-at before-start-time :retrigger-mock-for-build-number 3}}}))))))))

(deftest last-step-result-with-test
  (testing "that we can access the last step result for a particular step that has a value with a particular key and that it is independent of implemented order of the history-map"
    (let [history (into (sorted-map-by <) { 5 { [0 2] {:status :success :foo :foobar} [0 1] { :status :failure}}
                                            9 { [0 2] {:status :running} [0 1] { :status :failure}}
                                            8 { [0 2] {:status :success :foo nil} [0 1] { :status :failure}}
                                            7 { [0 2] {:status :success :foo :bar} [0 1] { :status :failure}}
                                            6 { [0 2] {:status :success :foo :baz} [0 1] { :status :failure}}})]
      (is (= {:status :success :foo :bar}
             (most-recent-step-result-with :foo
                                           (some-ctx-with
                                             :step-id [0 2]
                                             :initial-pipeline-state history)))))))

(deftest overall-build-status-test
  (testing "that the status will be running while the pipeline is still active"
    (is (= :running (overall-build-status {'(0) {:status :success :most-recent-update-at stop-time :first-updated-at start-time}
                                           '(1) {:status :running :most-recent-update-at stop-time :first-updated-at start-time}}))))
  (testing "that if everything is successful, the pipeline as a whole is successful"
    (is (= :success (overall-build-status {'(0) {:status :success :most-recent-update-at stop-time :first-updated-at start-time}}))))
  (testing "that a pipeline will still be in running state while there is a running step"
    (is (= :running (overall-build-status {'(0)   {:status :running :most-recent-update-at stop-time :first-updated-at start-time}
                                           '(1 0) {:status :running :most-recent-update-at stop-time :first-updated-at start-time}
                                           '(2 0) {:status :failure :most-recent-update-at stop-time :first-updated-at start-time}}))))
  (testing "that a pipeline will be a failure once there is a failed step"
    (is (= :failure (overall-build-status {'(1) {:status :success :most-recent-update-at stop-time :first-updated-at start-time}
                                           '(2) {:status :failure :most-recent-update-at stop-time :first-updated-at start-time}}))))
  (testing "that a killed step will be ignored"
    (is (= :success (overall-build-status {'(1 1) {:status :killed :most-recent-update-at stop-time :first-updated-at long-before-start-time :has-been-waiting true}
                                           '(2 1) {:status :success :most-recent-update-at long-after-stop-time :first-updated-at start-time :has-been-waiting true}
                                           '(1)   {:status :success :most-recent-update-at stop-time :first-updated-at start-time}
                                           '(2)   {:status :success :most-recent-update-at stop-time :first-updated-at start-time}}))))
  (testing "that a missing status leads to status unknown"
    (is (= :unknown (overall-build-status {'(0) {:no :status :most-recent-update-at stop-time :first-updated-at start-time}})))))

(deftest earliest-first-update-test
  (testing "that the earliest start time is the start time of the pipeline"
    (is (= before-start-time (earliest-first-update {'(2) {:status :success :most-recent-update-at stop-time :first-updated-at start-time}
                                                     '(1) {:status :success :most-recent-update-at stop-time :first-updated-at before-start-time}}))))
  (testing "that retriggered steps are ignored when searching the earliest start time"
    (is (= start-time (earliest-first-update {'(2) {:status :success :most-recent-update-at stop-time :first-updated-at start-time}
                                              '(1) {:status :success :most-recent-update-at stop-time :first-updated-at before-start-time :retrigger-mock-for-build-number 42}})))))

(deftest latest-most-recent-update-test
  (testing "that the most recent update will be the pipelines most recent update"
    (is (= after-stop-time (latest-most-recent-update {'(0) {:status :success :most-recent-update-at stop-time :first-updated-at start-time}
                                                       '(1) {:status :success :most-recent-update-at after-stop-time :first-updated-at start-time}})))))

(deftest build-that-was-retriggered-test
  (testing "that the retrigger-mock of the first build step indicates the build number that was retriggered for this build"
    (is (= 3 (build-that-was-retriggered {'(2) {:status :success :most-recent-update-at stop-time :first-updated-at start-time}
                                          '(1) {:status :success :most-recent-update-at stop-time :first-updated-at before-start-time :retrigger-mock-for-build-number 3}}))))
  (testing "that nil is returned if the build wasn't retriggered"
    (is (= nil (build-that-was-retriggered {'(2) {:status :success :most-recent-update-at stop-time :first-updated-at start-time}
                                            '(1) {:status :success :most-recent-update-at stop-time :first-updated-at before-start-time}})))))

(deftest build-duration-test
  (testing "the normal case"
    (is (= 20 (build-duration {'(2) {:status :success :most-recent-update-at after-twenty-sec :first-updated-at after-ten-sec}
                               '(1) {:status :success :most-recent-update-at after-ten-sec :first-updated-at start-time}}))))
  (testing "more than one minute"
    (is (= 90 (build-duration {'(2) {:status :success :most-recent-update-at after-one-minute-and-30-sec :first-updated-at after-ten-sec}
                               '(1) {:status :success :most-recent-update-at after-ten-sec :first-updated-at start-time}}))))
  (testing "can deal with hiccups in timestamps"
    (is (= 0 (build-duration {'(1) {:status :success :most-recent-update-at start-time :first-updated-at after-ten-sec}}))))
  (testing "retriggered pipeline takes as long as all intervals where the pipeline was active"
    (is (= 40 (build-duration {'(1 2) {:status :success :most-recent-update-at after-thirty-sec :first-updated-at after-ten-sec :retrigger-mock-for-build-number 10}
                               '(2 2) {:status :success :most-recent-update-at after-one-minute-and-40-sec :first-updated-at after-one-minute-and-30-sec}
                               '(2)   {:status :success :most-recent-update-at after-one-minute-and-40-sec :first-updated-at after-one-minute-and-30-sec}
                               '(1)   {:status :success :most-recent-update-at after-ten-sec :first-updated-at start-time :retrigger-mock-for-build-number 10}}))))
  (testing "steps that wait aren't considered"
    (is (= 20 (build-duration {'(3) {:status :success :most-recent-update-at after-thirty-sec :first-updated-at after-twenty-sec}
                               '(2) {:status :success :most-recent-update-at after-twenty-sec :first-updated-at after-ten-sec :has-been-waiting true}
                               '(1) {:status :success :most-recent-update-at after-ten-sec :first-updated-at start-time :retrigger-mock-for-build-number 10}}))))
  (testing "durations of child-steps aren't considered"
    (is (= 20 (build-duration {'(1 2) {:status :success :most-recent-update-at after-thirty-sec :first-updated-at after-ten-sec}
                               '(2)   {:status :success :most-recent-update-at after-twenty-sec :first-updated-at after-ten-sec}
                               '(1)   {:status :success :most-recent-update-at after-ten-sec :first-updated-at start-time}})))))
