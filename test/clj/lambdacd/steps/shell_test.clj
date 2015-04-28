(ns lambdacd.steps.shell-test
  (:require [clojure.test :refer :all]
            [clojure.core.async :as async]
            [lambdacd.testsupport.test-util :refer [result-channel->map]]
            [lambdacd.steps.shell :refer :all]))

(defn- some-ctx
  ([] (some-ctx (atom false)))
  ([is-killed]
  { :result-channel (async/chan 100)
    :is-killed is-killed} ))

(defn- kill-after-one-sec [is-killed]
  (async/thread
    (Thread/sleep 1000)
    (reset! is-killed true)))

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
    (testing "that exports work until we are able to set environment variables"
      (is (= "foo\n" (:out (bash (some-ctx)"/" "export X=foo" "echo $X")))))
    (testing "that the output channel is updated with every line"
      (let [some-ctx (some-ctx)
            result-channel (:result-channel some-ctx)]
        (bash some-ctx "/" "echo foo" "echo bar")
        (is (= [[:out "foo\n"]
                [:out "foo\nbar\n"]]
               (async/<!! (async/into [] result-channel)))))))

(deftest shell-cwd-test
  (testing "that the comand gets executed in the correct directory"
    (is (= "/\n" (:out (bash (some-ctx) "/" "pwd"))))))


(deftest kill-test
  (testing "that we are able to kill a running shell-step"
    (let [is-killed (atom false)]
      (kill-after-one-sec is-killed)
      (is (= {:exit   143
              :out    "foo\nbar\n"
              :status :killed}
             (bash (some-ctx is-killed) "/"
                "echo foo"
                "sleep .5"
                "echo bar"
                "sleep 5"
                "echo this-is-after-more-than-five-seconds-and-shouldnt-appear-in-output")))))
  (testing "that we are able to handle jobs that are already killed from the start"
    (let [is-killed (atom true)]
      (is (= {:exit   143
              :out    ""
              :status :killed}
             (bash (some-ctx is-killed) "/"
                   "echo foo"))))))