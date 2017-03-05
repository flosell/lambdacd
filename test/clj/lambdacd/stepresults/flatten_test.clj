(ns lambdacd.stepresults.flatten-test
  (:require [clojure.test :refer :all]
            [lambdacd.stepresults.flatten :refer :all]))

(deftest flatten-step-result-outputs-test
  (testing "that it works"
    (is (= {[1]   {:status :success}
            [2]   {:status  :success
                   :outputs {[1 2] {:status :success :step [1 2]}}}
            [1 2] {:status :success :step [1 2]}}
           (flatten-step-result-outputs {[1] {:status :success}
                                         [2] {:status  :success
                                              :outputs {[1 2] {:status :success :step [1 2]}}}})))))
