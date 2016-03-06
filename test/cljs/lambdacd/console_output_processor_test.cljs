(ns lambdacd.console-output-processor-test
  (:require [cljs.test :refer-macros [deftest is testing run-tests]]
            [lambdacd.console-output-processor :as p]))

(deftest process-escape-sequences
  (testing "that nil values and empty strings are supported"
    (is (= [] (p/process-ascii-escape-characters nil)))
    (is (= [] (p/process-ascii-escape-characters ""))))
  (testing "that we split on newlines"
    (is (= ["hello" "world"] (p/process-ascii-escape-characters "hello\nworld")))
    (is (= ["hello" "world"] (p/process-ascii-escape-characters "hello\r\nworld"))))
  (testing "that single carriage returns remove text prior to it"
    (is (= ["world"] (p/process-ascii-escape-characters "hello\rworld")))
    (is (= ["three"] (p/process-ascii-escape-characters "one\rtwo\rthree")))
    (is (= ["three" "four" "five"] (p/process-ascii-escape-characters "one\rtwo\rthree\nfour\r\nfive")))
    (is (= ["one" "" "two"] (p/process-ascii-escape-characters "one\n\r\ntwo")))
    (is (= ["one" "" "two"] (p/process-ascii-escape-characters "one\r\n\r\ntwo")))
    (is (= ["foure"] (p/process-ascii-escape-characters "three\rfour"))))
  (testing "that backspace removes the previous character"
    (is (= ["hello world"] (p/process-ascii-escape-characters "hello worldd\b")))
    (is (= ["hello world"] (p/process-ascii-escape-characters "hellll\b\bo world")))
    (is (= [""] (p/process-ascii-escape-characters "\b")))
    (is (= [""] (p/process-ascii-escape-characters "x\b\b")))
    (is (= ["x"] (p/process-ascii-escape-characters "\bx")))))
