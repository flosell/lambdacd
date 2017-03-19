(ns lambdacd.stepstatus.unify-test
  (:require [clojure.test :refer :all]
            [lambdacd.stepstatus.unify :refer :all]))

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

(deftest unify-only-status-test
  (testing "that the converted function returns a proper step-result"
    (is (= {:status :some-status} ((unify-only-status (constantly :some-status)) {}))))
  (testing "that it converts the given function into one that receives only statuses"
    (is (= [:foo :bar] (:status ((unify-only-status identity) {[0]{:status :foo} [1]{:status :bar}}))))))
