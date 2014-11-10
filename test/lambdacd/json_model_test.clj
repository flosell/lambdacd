(ns lambdacd.json-model-test
  (:use [lambdacd.test-util])
  (:require [clojure.test :refer :all]
            [lambdacd.json-model :refer :all]
            [lambdacd.util :as utils]
            [clojure.data.json :as json]))

(deftest pipeline-state->json-format-test
  (testing "that it converts to a decent-json-compatible format"
    (is (= #{{:step-id "0" :step-result { :status :success }}
             {:step-id "0-1-2" :step-result { :status :failure :out "something went wrong" }}}
           (into #{} (pipeline-state->json-format { [0] { :status :success }
                                          [0 1 2] { :status :failure :out "something went wrong" }}))))))

