(ns lambdacd.stepresults.merge-test
  (:require [clojure.test :refer :all]
            [lambdacd.stepresults.merge :refer :all]))

(defn some-merge-fn [map-a map-b]
  (assoc (merge map-a map-b)
    :something :extra))

(deftest merge-step-results-test
  (testing "that it can merge a list of step results"
    (is (= {:status :success
            :foo :bar
            :bar :baz
            :something :extra}
           (merge-step-results [{:status :success}
                                {:foo :bar}
                                {:bar :baz}]
                               some-merge-fn))))
  (testing "that later things overwrite earlier things"
    (is (= {:status :success
            :foo :baz}
           (merge-step-results [{:status :success}
                                {:foo :bar}
                                {:foo :baz}]
                               merge))))
  (testing "that an empty list merges to an empty result"
    (is (= {}
           (merge-step-results [] merge)))))

