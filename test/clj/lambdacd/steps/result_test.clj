(ns lambdacd.steps.result-test
  (:require [clojure.test :refer :all]
            [lambdacd.steps.result :refer :all]
            [lambdacd.testsupport.matchers :refer [map-containing]]
            [conjure.core :as c]
            [lambdacd.steps.status :as status]))

; ----------- common -----------------
(defn some-resolver [_ _ _] nil)
(defn some-other-resolver [_ _ _])
(defn some-third-resolver [_ _ _])

(deftest merge-step-results-test
  (testing "that it merges two steps and resolves conflicts using the passed resolvers"
    (testing "conflictless merging"
      (is (= {:foo "hello" :bar "world"} (merge-two-step-results {:foo "hello"} {:bar "world"}
                                                                 :resolvers []))))
    (testing "using the resolvers"
      (testing "the resolver gets called"
        (c/stubbing [some-resolver :resolved]
          (is (= {:foo :resolved} (merge-two-step-results {:foo :bar} {:foo :baz}
                                                          :resolvers [some-resolver])))
          (c/verify-called-once-with-args some-resolver :foo :bar :baz)))
      (testing "that the first matching resolver wins"
        (c/stubbing [some-resolver nil
                     some-other-resolver :resolved
                     some-third-resolver :also-resolved]
                    (is (= {:foo :resolved} (merge-two-step-results {:foo :bar} {:foo :baz}
                                                                    :resolvers [some-resolver some-other-resolver some-third-resolver])))))
      (testing "that conflicts will become nil if no resolver is matching"
        (is (= {:foo nil} (merge-two-step-results {:foo :bar} {:foo :baz}
                                                  :resolvers [some-resolver])))
        (is (= {:foo nil} (merge-two-step-results {:foo :bar} {:foo :baz}
                                                  :resolvers [])))))))


(deftest join-output-resolver-test
  (testing "that :out gets resolved by joining strings with newlines"
    (is (= "foo\nbar" (join-output-resolver :out "foo" "bar"))))
  (testing "that other keys don't resolve"
    (is (= nil (join-output-resolver :other-key "foo" "bar"))))
  (testing "that outs that arent strings don't resolve"
    (is (= nil (join-output-resolver :other-key 1 "bar")))
    (is (= nil (join-output-resolver :other-key "foo" :bar)))))



