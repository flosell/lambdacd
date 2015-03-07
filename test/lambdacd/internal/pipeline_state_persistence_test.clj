(ns lambdacd.internal.pipeline-state-persistence-test
  (:use [lambdacd.test-util])
  (:require [clojure.test :refer :all]
            [lambdacd.internal.pipeline-state-persistence :refer :all]))

(deftest pipeline-state->json-format-test
  (testing "that it converts to a decent-json-compatible format"
    (is (= #{{:step-id "0" :step-result { :status :success }}
             {:step-id "0-1-2" :step-result { :status :failure :out "something went wrong" }}}
           (into #{} (pipeline-state->json-format { [0] { :status :success }
                                          [0 1 2] { :status :failure :out "something went wrong" }}))))))

(deftest json-format->pipeline-state-test
  (testing "that it converts to a decent-json-compatible format"
    (is (= { [0] { :status :success }
             [0 1 2] { :status :failure :out "something went wrong" }}
           (json-format->pipeline-state [{:step-id "0" :step-result { :status :success }}
                                         {:step-id "0-1-2" :step-result { :status :failure :out "something went wrong" }}])))))

