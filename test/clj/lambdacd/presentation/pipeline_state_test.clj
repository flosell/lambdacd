(ns lambdacd.presentation.pipeline-state-test
  (:use [lambdacd.testsupport.test-util])
  (:require [clojure.test :refer :all]
            [lambdacd.presentation.pipeline-state :refer :all]))

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
