(ns lambdacd.stepresults.merge-resolvers-test
  (:require [clojure.test :refer :all]
            [lambdacd.stepresults.merge-resolvers :refer :all]
            [lambdacd.steps.status :as status]))

(deftest some-key :k)

(deftest join-output-resolver-test
  (testing "that :out gets resolved by joining strings with newlines"
    (is (= "foo\nbar" (join-output-resolver :out "foo" "bar"))))
  (testing "that other keys don't resolve"
    (is (= nil (join-output-resolver :other-key "foo" "bar"))))
  (testing "that outs that arent strings don't resolve"
    (is (= nil (join-output-resolver :other-key 1 "bar")))
    (is (= nil (join-output-resolver :other-key "foo" :bar)))))

(deftest merge-nested-maps-resolver-test
  (testing "that it returns nil if one of the two inputs is not a map"
    (is (= nil (merge-nested-maps-resolver some-key {} "x")))
    (is (= nil (merge-nested-maps-resolver some-key :x {} ))))
  (testing "that it merges to maps"
    (is (= {:foo :bar
            :bar :baz} (merge-nested-maps-resolver some-key
                                                   {:foo :bar}
                                                   {:bar :baz}))))
  (testing "that it's not a deep merge, the second one wins"
    (is (= {:nested {:bar :baz}} (merge-nested-maps-resolver some-key
                                                   {:nested {:foo :bar}}
                                                   {:nested {:bar :baz}})))))

(deftest status-resolver-test
  (testing "that it returns nil if the key to be merged is not status"
    (is (= nil (status-resolver :hello :success :failure))))
  (testing "that it delegates if it is a status"
    (with-redefs [status/choose-last-or-not-success (fn [s1 s2] (str s1 s2))]
      (is (= ":success:failure" (status-resolver :status :success :failure))))))

(deftest second-wins-resolver-test
  (testing "that it always returns the second argument"
    (is (= 2 (second-wins-resolver some-key 1 2)))
    (is (= 1 (second-wins-resolver some-key 2 1)))
    (is (= nil (second-wins-resolver some-key 2 nil)))))

(deftest combine-to-list-resolver-test
  (testing "that it concatenates two lists"
    (is (= [1 2 3 4] (combine-to-list-resolver some-key [1 2] [3 4]))))
  (testing "that it does stuff if only the first one is a list"
    (is (= [1 2 "test"] (combine-to-list-resolver some-key [1 2] "test")))) ; TODO: stuff
  (testing "that it returns nil if the first one is not a list"
    (is (= nil (combine-to-list-resolver some-key nil [3 4])))))
