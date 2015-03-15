(ns lambdacd.testsupport.matchers-test
  (:require [clojure.test :refer :all]
            [lambdacd.testsupport.matchers :refer :all]))


(deftest map-containing?-test
  (testing "that it matches when the key-value pairs of the expected map are in the actual map"
    (is (= true (map-containing {:foo :bar } {:foo :bar})))
    (is (= true (map-containing {:foo :bar } {:foo :bar :x :y})))
    (is (= false (map-containing {:foo :bar :x :y} {:foo :bar })))
    (is (= true (map-containing { } {:foo :bar :x :y})))
    (is (= false (map-containing { :hello :world } {:foo :bar :hello :joe})))
    (is (= false (map-containing { :foo :bar } {:something-nested { :foo :bar }})))
    ))
