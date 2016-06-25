(ns lambdacd.step-id-test
  (:use [lambdacd.testsupport.test-util])
  (:require [clojure.test :refer :all]
            [lambdacd.step-id :refer :all]))

(deftest later-or-before-test
  (testing "that [2] is after [1]"
    (is (later-than? [2] [1]))
    (is (not (before? [2] [1])))
    (is (not (later-than? [1] [2])))
    (is (before? [1] [2])))
  (testing "that [2] is after a child of [1]"
    (is (later-than? [2] [1 1]))
    (is (not (before? [2] [1 1] )))
    (is (later-than? [2] [2 1]))
    (is (not (before? [2] [2 1])))
    (is (not (later-than? [1 1] [2])))
    (is (before? [1 1] [2])))
  (testing "that a child of [2] is after a child of [1]"
    (is (later-than? [1 2] [1 1]))
    (is (not (before? [1 2] [1 1])))
    (is (later-than? [1 2] [1 1 1]))
    (is (not (before? [1 2] [1 1 1])))
    (is (later-than? [1 1 2] [1 1]))
    (is (not (later-than? [1 1] [1 1 2]))))
  (testing "that a child of [1] is after [1]"
    (is (later-than? [1 1] [1]))
    (is (not (later-than? [1] [1 1]))))
  (testing "that a step-id is not after or before itself"
    (is (not (later-than? [1 1] [1 1])))
    (is (not (before? [1 1] [1 1]))))
  (testing "that [3 2] is not before [2 2]"
    (is (not (before? [3 2] [2 2])))
    (is (later-than? [3 2] [2 2]))))

(deftest parent-relationship-test
  (testing "you need a common postfix to be a parent of something"
    (is (not (parent-of? [1] [1])))
    (is (parent-of? [1] [2 1]))
    (is (not (parent-of? [2 1] [1])))
    (is (parent-of? [2] [2 2]))
    (is (not (parent-of? [2 2] [2])))
    (is (parent-of? [2] [1 2 2]))
    (is (not (parent-of? [1 2 2] [2])))
    (is (not (parent-of? [1] [2 2 2])))))

(deftest direct-parent-test
  (testing "a direct child relationship"
    (is (not (direct-parent-of? [1] [1])))
    (is (direct-parent-of? [1] [2 1]))
    (is (not (direct-parent-of? [2 1] [1]))))
  (testing "recursive child relationship"
    (is (not (direct-parent-of? [2] [1 2 2])))))

(deftest root-step-id?-test
  (testing "that a root-step has a step-id with only one digit, i.e. it has no parent"
    (is (root-step-id? [1]))
    (is (not (root-step-id? [1 1])))))

(deftest root-step-id-test
  (testing "that root-step-id is the last element of the step id"
    (is (= 1 (root-step-id-of [1])))
    (is (= 1 (root-step-id-of [3 2 1])))))