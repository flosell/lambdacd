(ns lambdaci.shell-test
  (:require [clojure.test :refer :all]
            [lambdaci.shell :refer :all]))

(deftest shell-return-code-test
  (testing "that bash returns the right return code for a single successful command"
    (is (= 0 (:exit (bash "/" "exit 0")))))
  (testing "that bash returns the right return code for a single failing command"
    (is (= 1 (:exit (bash "/" "exit 1")))))
  (testing "that bash returns the right return code for a series of commands"
    (is (= 1 (:exit (bash "/" "echo foo" "echo bar" "exit 1" "echo baz")))))
  )

(deftest shell-output-test
  (testing "that bash returns the right output for a single command"
    (is (= "foo\n" (:out (bash "/" "echo foo")))))
  (testing "that bash returns the right output for a series of commands"
    (is (= "foo\nbar\n" (:out (bash "/" "echo foo" "echo bar" "exit 1" "echo baz")))))
  (testing "that the output also contains stderr"
    (is (= "foo\nerror\nbaz\n" (:out (bash "/" "echo foo" ">&2 echo error" "echo baz")))))
  )
