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
  (testing "that it converts the internal pipeline-state into a more readable history-representation"
    (is (= [{:build-number 5
             :status :running
             :most-recent-update-at after-stop-time
             :first-updated-at start-time }
            {:build-number 6
             :status :success
             :most-recent-update-at stop-time
             :first-updated-at start-time}
            {:build-number 7
             :status :failure
             :most-recent-update-at stop-time
             :first-updated-at before-start-time}
            {:build-number 8
             :status :waiting
             :most-recent-update-at stop-time
             :first-updated-at start-time}
            {:build-number 9
             :status :unknown
             :most-recent-update-at stop-time
             :first-updated-at start-time}
            {:build-number 10
             :status :success
             :most-recent-update-at stop-time
             :first-updated-at start-time}
            ] (history-for { 5  {[0]   { :status :success :most-recent-update-at stop-time :first-updated-at start-time }
                                 [1]   { :status :running :most-recent-update-at after-stop-time :first-updated-at start-time}}
                             6  {[0]   { :status :success :most-recent-update-at stop-time :first-updated-at start-time}}
                             7  {[0 2] { :status :running :most-recent-update-at stop-time :first-updated-at start-time}
                                 [0 1] { :status :failure :most-recent-update-at stop-time :first-updated-at before-start-time}}
                             8  {[0]   { :status :waiting :most-recent-update-at stop-time :first-updated-at start-time}}
                             9  {[0]   { :no :status      :most-recent-update-at stop-time :first-updated-at start-time}}
                             10 {[1 1] { :status :killed  :most-recent-update-at stop-time :first-updated-at long-before-start-time :has-been-waiting true}
                                 [2 1] { :status :success :most-recent-update-at long-after-stop-time :first-updated-at start-time :has-been-waiting true}
                                 [1]   { :status :success :most-recent-update-at stop-time :first-updated-at start-time}
                                 [2]   { :status :success :most-recent-update-at stop-time :first-updated-at start-time}}})))))

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
