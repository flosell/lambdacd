(ns lambdacd.console-output-processor-test
  (:require [cljs.test :refer-macros [deftest is testing run-tests]]
            [lambdacd.console-output-processor :as p]))

(deftest process-escaped-ansi-text
  (testing "ascii escape character handling"
    (testing "that nil values and empty strings are supported"
      (is (= [[{:text ""}]] (p/process-ascii-escape-characters nil)))
      (is (= [[{:text ""}]] (p/process-ascii-escape-characters ""))))
    (testing "that we split on newlines"
      (is (= [[{:text "hello"}] [{:text "world"}]] (p/process-ascii-escape-characters "hello\nworld")))
      (is (= [[{:text "hello"}] [{:text "world"}]] (p/process-ascii-escape-characters "hello\r\nworld"))))
    (testing "that single carriage returns remove text prior to it"
      (is (= [[{:text "world"}]] (p/process-ascii-escape-characters "hello\rworld")))
      (is (= [[{:text "three"}]] (p/process-ascii-escape-characters "one\rtwo\rthree")))
      (is (= [[{:text "three"}] [{:text "four"}] [{:text "five"}]] (p/process-ascii-escape-characters "one\rtwo\rthree\nfour\r\nfive")))
      (is (= [[{:text "one"}] [{:text ""}] [{:text "two"}]] (p/process-ascii-escape-characters "one\n\r\ntwo")))
      (is (= [[{:text "one"}] [{:text ""}] [{:text "two"}]] (p/process-ascii-escape-characters "one\r\n\r\ntwo")))
      (is (= [[{:text "foure"}]] (p/process-ascii-escape-characters "three\rfour"))))
    (testing "that backspace removes the previous character"
      (is (= [[{:text "hello world"}]] (p/process-ascii-escape-characters "hello worldd\b")))
      (is (= [[{:text "hello world"}]] (p/process-ascii-escape-characters "hellll\b\bo world")))
      (is (= [[{:text ""}]] (p/process-ascii-escape-characters "\b")))
      (is (= [[{:text ""}]] (p/process-ascii-escape-characters "x\b\b")))
      (is (= [[{:text "x"}]] (p/process-ascii-escape-characters "\bx")))))
  (testing "ansi escape character handling"
    (testing "that we support bold text"
      (is (= [[{:text "test" :bold true}]] (p/process-ascii-escape-characters "\033[1mtest\033[0m"))))
    (testing "that we support italic text"
      (is (= [[{:text "test" :italic true}]] (p/process-ascii-escape-characters "\033[3mtest\033[0m"))))
    (testing "that we support underlined text"
      (is (= [[{:text "test" :underline true}]] (p/process-ascii-escape-characters "\033[4mtest\033[0m"))))
    (testing "that we support foreground colors"
      (is (= [[{:text "test" :foreground "black"}]] (p/process-ascii-escape-characters "\033[30mtest\033[0m")))
      (is (= [[{:text "test" :foreground "red"}]] (p/process-ascii-escape-characters "\033[31mtest\033[0m")))
      (is (= [[{:text "test" :foreground "green"}]] (p/process-ascii-escape-characters "\033[32mtest\033[0m")))
      (is (= [[{:text "test" :foreground "yellow"}]] (p/process-ascii-escape-characters "\033[33mtest\033[0m")))
      (is (= [[{:text "test" :foreground "blue"}]] (p/process-ascii-escape-characters "\033[34mtest\033[0m")))
      (is (= [[{:text "test" :foreground "magenta"}]] (p/process-ascii-escape-characters "\033[35mtest\033[0m")))
      (is (= [[{:text "test" :foreground "cyan"}]] (p/process-ascii-escape-characters "\033[36mtest\033[0m")))
      (is (= [[{:text "test" :foreground "white"}]] (p/process-ascii-escape-characters "\033[37mtest\033[0m")))
      (is (= [[{:text "test" :foreground "grey"}]] (p/process-ascii-escape-characters "\033[90mtest\033[0m"))))
    (testing "that we support background colors"
      (is (= [[{:text "test" :background "black"}]] (p/process-ascii-escape-characters "\033[40mtest\033[0m")))
      (is (= [[{:text "test" :background "red"}]] (p/process-ascii-escape-characters "\033[41mtest\033[0m")))
      (is (= [[{:text "test" :background "green"}]] (p/process-ascii-escape-characters "\033[42mtest\033[0m")))
      (is (= [[{:text "test" :background "yellow"}]] (p/process-ascii-escape-characters "\033[43mtest\033[0m")))
      (is (= [[{:text "test" :background "blue"}]] (p/process-ascii-escape-characters "\033[44mtest\033[0m")))
      (is (= [[{:text "test" :background "magenta"}]] (p/process-ascii-escape-characters "\033[45mtest\033[0m")))
      (is (= [[{:text "test" :background "cyan"}]] (p/process-ascii-escape-characters "\033[46mtest\033[0m")))
      (is (= [[{:text "test" :background "white"}]] (p/process-ascii-escape-characters "\033[47mtest\033[0m"))))
    (testing "multiple escape characters"
      (is (= [[{:text "test" :bold true :foreground "red"}]] (p/process-ascii-escape-characters "\033[1m\033[31mtest\033[0m")))
      (is (= [[{:text "bold" :bold true}
               {:text "red" :foreground "red"}]] (p/process-ascii-escape-characters "\033[1mbold\033[0m\033[31mred\033[0m")))))
  (testing "a mix of ascii and ansi escape characters"
    (testing "a newline within a bold text"
      (is (= [[{:text "test" :bold true}]
              [{:text "test2" :bold true} {:text "test3"}]] (p/process-ascii-escape-characters "\033[1mtest\ntest2\033[0mtest3"))))
    (testing "backspaces that remove ansi control characters (not sure this is the right behavior so just documenting here)"
      (is (= [[{:text "test"}]] (p/process-ascii-escape-characters "\033[1m\b\b\b\btest\033[0m"))))))

(deftest split-newlines-test
  (testing "that a fragment without metadata remains untouched"
    (is (= [{:text "foobar" :some-data true}] (p/split-fragments-on-newline {:text "foobar" :some-data true}))))
  (testing "that a fragment that contains a newline is split into two fragments with the same metadata"
    (is (= [{:text "foo" :some-data true} :newline {:text "bar" :some-data true}] (p/split-fragments-on-newline {:text "foo\nbar" :some-data true}))))
  (testing "that a fragment that contains only a newline results in the newline control keyword"
    (is (= [:newline] (p/split-fragments-on-newline {:text "\n" :some-data true}))))
  (testing "that a fragment with an empty text remains untouched"
    (is (= [{:text ""}] (p/split-fragments-on-newline {:text ""})))))