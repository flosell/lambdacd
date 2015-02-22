(ns lambdacd.shell-test
  (:require [clojure.test :refer :all]
            [clojure.core.async :as async]
            [lambdacd.test-util :refer [result-channel->map]]
            [lambdacd.shell :refer :all]))

(defn- some-ctx []
  { :result-channel (async/chan 100)} )

(deftest shell-return-code-test
  (testing "that bash returns the right return code for a single successful command"
    (is (= 0 (:exit (bash (some-ctx) "/" "exit 0")))))
  (testing "that bash returns the right return code for a single failing command"
    (is (= 1 (:exit (bash (some-ctx) "/" "exit 1")))))
  (testing "that bash returns the right return code for a series of commands"
    (is  (= 1 (:exit (bash (some-ctx) "/" "echo foo" "echo bar" "exit 1" "echo baz")))))
  )

(deftest shell-output-test
    (testing "that bash returns the right output for a single command"
      (is (= "foo\n" (:out (bash (some-ctx) "/" "echo foo")))))
    (testing "that bash returns the right output for a series of commands"
      (is (= "foo\nbar\n" (:out (bash (some-ctx)"/" "echo foo" "echo bar" "exit 1" "echo baz")))))
    (testing "that the output also contains stderr"
      (is (= "foo\nerror\nbaz\n" (:out (bash (some-ctx)"/" "echo foo" ">&2 echo error" "echo baz")))))
    (testing "that the output channel is updated with every line"
      (let [some-ctx (some-ctx)
            result-channel (:result-channel some-ctx)]
        (bash some-ctx "/" "echo foo" "echo bar")
        (is (= [[:out "foo\n"]
                [:out "foo\nbar\n"]]
               (async/<!! (async/into [] result-channel))))))
    )

(deftest shell-cwd-test
  (let [some-ctx { :result-channel (async/chan 100)}]
    (testing "that the comand gets executed in the correct directory"
    (is (= "/\n" (:out (bash some-ctx "/" "pwd")))))))