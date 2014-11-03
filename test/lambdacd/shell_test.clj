(ns lambdacd.shell-test
  (:require [clojure.test :refer :all]
            [clojure.core.async :as async]
            [lambdacd.test-util :refer [result-channel->map]]
            [lambdacd.shell :refer :all]))

(deftest shell-return-code-test
  (testing "that bash returns the right return code for a single successful command"
    (is (= 0 (:exit (result-channel->map (bash "/" "exit 0"))))))
  (testing "that bash returns the right return code for a single failing command"
    (is (= 1 (:exit (result-channel->map (bash "/" "exit 1"))))))
  (testing "that bash returns the right return code for a series of commands"
    (is  (= 1 (:exit (result-channel->map (bash "/" "echo foo" "echo bar" "exit 1" "echo baz"))))))
  )

(deftest shell-output-test
  (testing "that bash returns the right output for a single command"
    (is (= "foo\n" (:out (result-channel->map (bash "/" "echo foo"))))))
  (testing "that bash returns the right output for a series of commands"
    (is (= "foo\nbar\n" (:out (result-channel->map (bash "/" "echo foo" "echo bar" "exit 1" "echo baz"))))))
  (testing "that the output also contains stderr"
    (is (= "foo\nerror\nbaz\n" (:out (result-channel->map (bash "/" "echo foo" ">&2 echo error" "echo baz"))))))
  (testing "that the output channel is updated with every line"
    (is (= [[:out "foo\n"]
            [:out "foo\nbar\n"]
            [:exit 0]
            [:status :success]]
           (async/<!! (async/into [] (bash "/" "echo foo" "echo bar"))))))
  )

(deftest shell-cwd-test
  (testing "that the comand gets executed in the correct directory"
    (is (= "/\n" (:out (result-channel->map (bash "/" "pwd")))))))