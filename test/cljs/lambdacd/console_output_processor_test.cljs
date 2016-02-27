(ns lambdacd.console-output-processor-test
  (:require [cljs.test :refer-macros [deftest is testing run-tests]]
            [lambdacd.console-output-processor :as p]))

(deftest process-escape-sequences
  (testing "that we split on newlines"
    (is (= ["hello" "world"] (p/process-ascii-escape-characters "hello\nworld")))
    (is (= ["hello" "world"] (p/process-ascii-escape-characters "hello\r\nworld"))))
  (testing "that single carriage returns remove text prior to it"
    (is (= ["world"] (p/process-ascii-escape-characters "hello\rworld")))
    (is (= ["three"] (p/process-ascii-escape-characters "one\rtwo\rthree")))
    (is (= ["three" "four" "five"] (p/process-ascii-escape-characters "one\rtwo\rthree\nfour\r\nfive")))
    (is (= ["one" "" "two"] (p/process-ascii-escape-characters "one\n\r\ntwo")))
    (is (= ["one" "" "two"] (p/process-ascii-escape-characters "one\r\n\r\ntwo")))))
