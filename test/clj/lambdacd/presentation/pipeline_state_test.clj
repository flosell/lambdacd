(ns lambdacd.presentation.pipeline-state-test
  (:use [lambdacd.testsupport.test-util])
  (:require [clojure.test :refer :all]
            [lambdacd.presentation.pipeline-state :refer :all]
            [clj-time.core :as t]))

(def start-time (t/now))
(def before-start-time (t/minus start-time (t/seconds 10)))
(def stop-time (t/plus start-time (t/seconds 10)))
(def after-stop-time (t/plus stop-time (t/seconds 10)))
(def long-before-start-time (t/minus start-time (t/hours 10)))
(def long-after-stop-time (t/plus stop-time (t/hours 10)))

(deftest history-test
  (testing "that the timestamps are accumulated correctly"
    (testing "that the earliest start time is the start time of the pipeline"
      (is (= [{:build-number 7
               :status :success
               :most-recent-update-at stop-time
               :first-updated-at before-start-time}]
             (history-for { 7  {[0 2] { :status :success :most-recent-update-at stop-time :first-updated-at start-time}
                                [0 1] { :status :success :most-recent-update-at stop-time :first-updated-at before-start-time}}}))))
    (testing "that the most recent update will be the pipelines most recent update"
      (is (= [{:build-number 5
               :status :success
               :most-recent-update-at after-stop-time
               :first-updated-at start-time }]
             (history-for {5  {[0]   { :status :success :most-recent-update-at stop-time :first-updated-at start-time }
                               [1]   { :status :success :most-recent-update-at after-stop-time :first-updated-at start-time}}})))))
  (testing "that the build-status is accumulated correctly"
    (testing "that the status will be running while the pipeline is still active"
      (is (= [{:build-number 5
               :status :running
               :most-recent-update-at stop-time
               :first-updated-at start-time }]
             (history-for {5  {[0]   { :status :success :most-recent-update-at stop-time :first-updated-at start-time }
                               [1]   { :status :running :most-recent-update-at stop-time :first-updated-at start-time}}}))))
    (testing "that if everything is successful, the pipeline as a whole is successful"
      (is (= [{:build-number 6
               :status :success
               :most-recent-update-at stop-time
               :first-updated-at start-time}]
             (history-for { 6  {[0]   { :status :success :most-recent-update-at stop-time :first-updated-at start-time}}}))))
    (testing "that a pipeline will still be in running state while there is a running step"
      (is (= [{:build-number 7
               :status :running
               :most-recent-update-at stop-time
               :first-updated-at start-time}]
             (history-for { 7  {[0 2] { :status :running :most-recent-update-at stop-time :first-updated-at start-time}
                                [0 1] { :status :failure :most-recent-update-at stop-time :first-updated-at start-time}}}))))
    (testing "that a pipeline will be a failure once there is a failed step"
      (is (= [{:build-number 7
               :status :failure
               :most-recent-update-at stop-time
               :first-updated-at start-time}]
             (history-for { 7  {[0 2] { :status :success :most-recent-update-at stop-time :first-updated-at start-time}
                                [0 1] { :status :failure :most-recent-update-at stop-time :first-updated-at start-time}}}))))
    (testing "that a killed step will be ignored"
      (is (= [{:build-number 10
               :status :success
               :most-recent-update-at stop-time
               :first-updated-at start-time}]
             (history-for {10 {[1 1] { :status :killed  :most-recent-update-at stop-time :first-updated-at long-before-start-time :has-been-waiting true}
                               [2 1] { :status :success :most-recent-update-at long-after-stop-time :first-updated-at start-time :has-been-waiting true}
                               [1]   { :status :success :most-recent-update-at stop-time :first-updated-at start-time}
                               [2]   { :status :success :most-recent-update-at stop-time :first-updated-at start-time}}}))))
    (testing "that more than one item works and a missing status leads to status unknown"
      (is (= [{:build-number 8
               :status :waiting
               :most-recent-update-at stop-time
               :first-updated-at start-time}
              {:build-number 9
               :status :unknown
               :most-recent-update-at stop-time
               :first-updated-at start-time}]
             (history-for {8  {[0]   { :status :waiting :most-recent-update-at stop-time :first-updated-at start-time}}
                           9  {[0]   { :no :status      :most-recent-update-at stop-time :first-updated-at start-time}}}))))))

(deftest most-recent-build-test
  (testing "that it returns the most recent build number in the pipeline-state"
    (is (= 9 (most-recent-build-number-in { 5 { }
                                            6 {  }
                                            9 { }})))))

(deftest last-step-result-with-test
  (testing "that we can access the last step result for a particular step that has a value with a particular key and that it is independent of implemented order of the history-map"
    (let [history { 5 { [0 2] {:status :success :foo :foobar} [0 1] { :status :failure}}
                   9 { [0 2] {:status :running} [0 1] { :status :failure}}
                   8 { [0 2] {:status :success :foo nil} [0 1] { :status :failure}}
                   7 { [0 2] {:status :success :foo :bar} [0 1] { :status :failure}}
                   6 { [0 2] {:status :success :foo :baz} [0 1] { :status :failure}}}]
      (is (= {:status :success :foo :bar}
             (most-recent-step-result-with :foo
               {:step-id [0 2]
                :_pipeline-state
                         (atom (into (sorted-map-by >) history))})))
      (is (= {:status :success :foo :bar}
             (most-recent-step-result-with :foo
                                           {:step-id [0 2]
                                            :_pipeline-state
                                                     (atom (into (sorted-map-by <) history))}))))))
