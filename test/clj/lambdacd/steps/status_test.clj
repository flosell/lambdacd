(ns lambdacd.steps.status-test
  (:require [clojure.test :refer :all]
            [lambdacd.steps.status :refer :all]))

(deftest successful-when-one-successful-test
  (testing "that one successful step is enough to make the unified view successful"
    (is (= :success (successful-when-one-successful [:success :error])))
    (is (= :success (successful-when-one-successful [:waiting :success])))
    (is (= :success (successful-when-one-successful [:waiting :success])))
    (is (= :success (successful-when-one-successful [:running :success]))))
  (testing "that everything needs to be have failed for the view to fail"
    (is (= :success (successful-when-one-successful [:failure :success])))
    (is (= :running (successful-when-one-successful [:failure :running])))
    (is (= :waiting (successful-when-one-successful [:failure :waiting])))
    (is (= :failure (successful-when-one-successful [:failure]))))
  (testing "that a running and no finished steps make the unified view running"
    (is (= :running (successful-when-one-successful [:running :waiting]))))
  (testing "that all waiting makes the unified view waiting"
    (is (= :waiting (successful-when-one-successful [:waiting]))))
  (testing "that all killed makes the unified view killed"
    (is (= :killed (successful-when-one-successful [:killed :killed])))
    (is (= :waiting (successful-when-one-successful [:killed :waiting]))))
  (testing "undefined otherwise"
    (is (= :unknown (successful-when-one-successful [:foo :bar])))))

(deftest successful-when-all-successful-test
  (testing "that that all successful steps make a successful unified view"
    (is (= :success (successful-when-all-successful [:success])))
    (is (= :success (successful-when-all-successful [:success :success]))))
  (testing "it doesnt fail as long as something is running"
    (is (= :failure (successful-when-all-successful [:failure])))
    (is (= :running (successful-when-all-successful [:failure :running])))
    (is (= :waiting (successful-when-all-successful [:failure :waiting])))
    (is (= :failure (successful-when-all-successful [:success :failure]))))
  (testing "that one waiting makes the unified view waiting when nothing's running"
    (is (= :waiting (successful-when-all-successful [:success :waiting]))))
  (testing "that one running step makes the unified view running"
    (is (= :running (successful-when-all-successful [:running :waiting])))
    (is (= :running (successful-when-all-successful [:running])))
    (is (= :running (successful-when-all-successful [:running :success])))
    (is (= :running (successful-when-all-successful [:killed :running]))))
  (testing "undefined otherwise"
    (is (= :unknown (successful-when-all-successful [:foo :bar])))))

(deftest choose-last-or-not-success-test
  (testing "everything not success wins over success"
    (is (= :success (choose-last-or-not-success :success :success)))
    (is (= :failure (choose-last-or-not-success :success :failure)))
    (is (= :failure (choose-last-or-not-success :failure :success)))
    (is (= :unknown (choose-last-or-not-success :unknown :success))))
  (testing "that if none of the two is success, choose the latter"
    (is (= :unknown (choose-last-or-not-success :failure :unknown)))
    (is (= :failure (choose-last-or-not-success :unknown :failure)))))

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
