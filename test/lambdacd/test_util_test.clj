(ns lambdacd.test-util-test
  (:require [clojure.test :refer :all]
            [lambdacd.test-util :refer :all]))

(defn some-function-changing-an-atom [a]
  (reset! a "hello")
  (reset! a "world"))

(deftest atom-history-test
  (testing "that we can record the history of an atom"
    (let [some-atom (atom "")]
      (is (= ["hello" "world"]
             (atom-history-for some-atom (some-function-changing-an-atom some-atom)))))))
