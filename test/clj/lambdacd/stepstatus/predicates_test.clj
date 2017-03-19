(ns lambdacd.stepstatus.predicates-test
  (:require [clojure.test :refer :all]
            [lambdacd.stepstatus.predicates :refer :all]))

(deftest is-active-test
  (testing "active statuses"
    (is (is-active? :running))
    (is (is-active? :waiting)))
  (testing "finished-statuses"
    (is (not (is-active? :success)))
    (is (not (is-active? :failure)))
    (is (not (is-active? :killed)))
    (is (not (is-active? :dead)))
    (is (not (is-active? :some-unknown-status)))))

