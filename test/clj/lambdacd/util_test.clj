(ns lambdacd.util-test
  (:use [lambdacd.util])
  (:require [clojure.test :refer :all]
            [clojure.core.async :as async]
            [clojure.java.io :as io]))

(deftest range-test
  (testing "that range produces a range from a value+1 with a defined length"
    ; TODO: the plus-one is like that because the user wants it, probably shouldn't be like this..
    (is (= '(6 7 8) (range-from 5 3)))))

(defn some-function [] {})


(deftest map-if-test
  (testing "that is applies a function to all elements that match a predicate"
    (is (= [] (map-if (identity true) inc [])))
    (is (= [4 3 5] (map-if #(< % 5) inc [3 2 4])))
    (is (= [3 2 5] (map-if #(= 4 %) inc [3 2 4])))))

(deftest create-temp-dir-test
  (testing "creating in default tmp folder"
    (testing "that we can create a temp-directory"
      (is (.exists (io/file (create-temp-dir)))))
    (testing "that it is writable"
      (is (.mkdir (io/file (create-temp-dir) "hello")))))
  (testing "creating in a defined parent directory"
    (testing "that it is a child of the parent directory"
      (let [parent (create-temp-dir)]
        (is (= parent (.getParent (io/file (create-temp-dir parent)))))))))


(deftest json-test
  (testing "that a proper ring-json-response is returned"
    (is (= {:body    "{\"hello\":\"world\"}"
            :headers {"Content-Type" "application/json"}
            :status  200} (json { :hello :world })))))

(deftest parse-int-test
  (testing "that we can parse integers"
    (is (= 42 (parse-int "42")))
    (is (= -1 (parse-int "-1")))
    (is (thrown? NumberFormatException (parse-int "foo")))))

(deftest fill-test
  (testing "that we can fill up a sequence to a certain length"
    (is (= [1 2 3 -1 -1] (fill [1 2 3] 5 -1))))
  (testing "that a collection is left just as it was if it is already longer than the desired length"
    (is (= [1 2 3] (fill [1 2 3] 2 -1)))
    (is (= [1 2 3] (fill [1 2 3] 3 -1)))))
