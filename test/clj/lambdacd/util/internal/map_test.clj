(ns lambdacd.util.internal.map-test
  (:require [clojure.test :refer :all]
            [lambdacd.util.internal.map :refer :all]
            [conjure.core :as c]))

(deftest put-if-not-present-test
  (testing "that it adds a value to a map only of no value exists for this key"
    (is (= {:foo :bar}       (put-if-not-present {:foo :bar} :foo :baz)))
    (is (= {:foo :baz}       (put-if-not-present {} :foo :baz)))
    (is (= {:a :b :foo :baz} (put-if-not-present {:a :b} :foo :baz)))))

(defn some-function [k v1 v2]
  (str k v1 v2))

(deftest merge-with-k-v-test
  (testing "that conflicts get passed to the passed function"
    (c/mocking [some-function]
               (merge-with-k-v some-function {:foo 1} {:foo 2})
               (c/verify-first-call-args-for some-function :foo 1 2)))
  (testing "a merge"
    (is (= {:foo ":foo12" :a 1 :b 2} (merge-with-k-v some-function {:foo 1 :a 1} {:foo 2 :b 2})))))
