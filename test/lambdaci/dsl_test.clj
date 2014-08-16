(ns lambdaci.dsl-test
  (:require [clojure.test :refer :all]
            [lambdaci.dsl :refer :all]))

(deftest step-result-merge
  (testing "merging without collisions"
    (is (= {:foo "hello" :bar "world"} (merge-step-results {:foo "hello"} {:bar "world"}))))
  (testing "merging without collisions"
    (is (= {:foo ["hello" "world"]} (merge-step-results {:foo "hello"} {:foo "world"})))))
  ;;(testing "merging into a flat list on collision"
  ;;  (is (= {:foo ["hello" "world" "test"]} (merge-step-results {:foo ["hello" "world"]} {:foo "test"}))))
