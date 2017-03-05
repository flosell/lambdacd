(ns lambdacd.stepresults.merge-test
  (:require [clojure.test :refer :all]
            [lambdacd.stepresults.merge :refer :all]
            [conjure.core :as c]))

(defn some-resolver [_ _ _] nil)
(defn some-other-resolver [_ _ _])
(defn some-third-resolver [_ _ _])

(defn some-merge-fn [map-a map-b]
  (assoc (merge map-a map-b)
    :something :extra))

(deftest merge-step-results-test
  (testing "that it can merge a list of step results"
    (is (= {:status    :success
            :foo       :bar
            :bar       :baz
            :something :extra}
           (merge-step-results [{:status :success}
                                {:foo :bar}
                                {:bar :baz}]
                               some-merge-fn))))
  (testing "that later things overwrite earlier things"
    (is (= {:status :success
            :foo    :baz}
           (merge-step-results [{:status :success}
                                {:foo :bar}
                                {:foo :baz}]
                               merge))))
  (testing "that an empty list merges to an empty result"
    (is (= {}
           (merge-step-results [] merge)))))


(deftest merge-two-step-results-test
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
                                                  :resolvers []))))))
  (testing "defaults"
    (testing "that it merges statuses, maps and in doubt, the last wins"
      (is (= {:status :failure
              :m      {:a :b
                       :b :c}
              :s      "b"}
             (merge-two-step-results {:status :failure
                                      :m      {:a :b}
                                      :s      "a"}
                                     {:status :success
                                      :m      {:b :c}
                                      :s      "b"}))))))
