(ns lambdacd.execution.internal.util-test
  (:require [clojure.test :refer :all]
            [lambdacd.execution.internal.util :refer :all]))

(deftest step-result-merge-test
  (testing "merging without collisions"
    (is (= {:foo "hello" :bar "world"} (merge-two-step-results {:foo "hello"} {:bar "world"}))))
  (testing "merging with value-collisions on keyword overwrites"
    (is (= {:status :failure} (merge-two-step-results {:status :success} {:status :failure}))))
  (testing "merging with value-collisions on keyword with values overwrites"
    (is (= {:exit 1} (merge-two-step-results {:exit 0 } {:exit 1}))))
  (testing "merging of nested maps"
    (is (= {:outputs {[1 0] {:foo :baz} [2 0] {:foo :baz}}}
           (merge-two-step-results {:outputs {[1 0] {:foo :baz}}} {:outputs { [2 0] {:foo :baz}}}))))
  (testing "merging into a flat list on collision"
    (is (= {:foo ["hello" "world" "test"]} (merge-two-step-results {:foo ["hello" "world"]} {:foo "test"})))))

