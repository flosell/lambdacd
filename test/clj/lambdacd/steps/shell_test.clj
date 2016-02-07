(ns lambdacd.steps.shell-test
  (:require [clojure.test :refer :all]
            [clojure.core.async :as async]
            [lambdacd.testsupport.test-util :refer [result-channel->map slurp-chan]]
            [lambdacd.testsupport.data :refer [some-ctx some-ctx-with]]
            [lambdacd.steps.shell :refer :all]
            [lambdacd.event-bus :as event-bus]))

(defn- contains-bar-output [[k v]]
  (and
    (= :out k)
    (.contains v "bar")))

(defn- kill-after-bar-is-echoed [ctx]
  (async/go-loop []
    (if (contains-bar-output (async/<! (:result-channel ctx)))
      (reset! (:is-killed ctx) true)
      (recur))))

(deftest shell-return-code-test
  (testing "that bash returns the right return code for a single successful command"
    (is (= 0 (:exit (bash (some-ctx) "/" "exit 0")))))
  (testing "that bash returns the right return code for a single failing command"
    (is (= 1 (:exit (bash (some-ctx) "/" "exit 1")))))
  (testing "that bash returns fails if the exit code isn't 0"
    (is (= :failure (:status (bash (some-ctx) "/" "exit 1")))))
  (testing "that bash returns the right return code for a series of commands"
    (is  (= 1 (:exit (bash (some-ctx) "/" "echo foo" "echo bar" "exit 1" "echo baz"))))))

(deftest shell-output-test
    (testing "that bash returns the right output for a single command"
      (is (= "foo\n" (:out (bash (some-ctx) "/" "echo foo")))))
    (testing "that bash returns the right output for a series of commands"
      (is (= "foo\nbar\n" (:out (bash (some-ctx) "/" "echo foo" "echo bar" "exit 1" "echo baz")))))
    (testing "that the output also contains stderr"
      (is (= "foo\nerror\nbaz\n" (:out (bash (some-ctx) "/" "echo foo" ">&2 echo error" "echo baz")))))
    (testing "that the output channel is updated with every line"
      (let [some-ctx (some-ctx-with :result-channel (async/chan 100))
            result-channel (:result-channel some-ctx)]
        (bash some-ctx "/" "echo foo" "echo bar")
        (is (= [[:out "foo\n"]
                [:out "foo\nbar\n"]]
               (slurp-chan result-channel))))))

(deftest shell-cwd-test
  (testing "that the comand gets executed in the correct directory"
    (is (= "/\n" (:out (bash (some-ctx) "/" "pwd"))))))

(deftest env-variables-test
  (testing "that we can add environment variables"
    (is (= "say hello\nsay world\n" (:out (bash (some-ctx) "/"
                             {"HELLO" "say hello"
                              "WORLD" "say world"}
                             "echo $HELLO"
                             "echo $WORLD"))))))

(deftest shell-execution-test
  (testing "that execution of commands stops after a command had an error"
    (let [result (bash (some-ctx) "/"
                       "echo hello"
                       "test 1 -eq 2"
                       "echo this-shouldnt-be-reached")]
      (is (= "hello\n" (:out result)))
      (is (= 1 (:exit result)))))
  (testing "that it supports commands that contain single-quotes"
    (is (= "Hello World\n" (:out (bash (some-ctx) "/" "echo 'Hello World'"))))))

(deftest kill-test
  (testing "that we are able to kill a running shell-step"
    (let [is-killed (atom false)
          ctx       (some-ctx-with :is-killed is-killed)]
      (kill-after-bar-is-echoed ctx)
      (is (= {:exit   143
              :out    "foo\nbar\n"
              :status :killed}
             (bash ctx "/"
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
             (bash (some-ctx-with :is-killed is-killed) "/"
                   "sleep 5"
                   "echo foo")))))
  (testing "that we inform the user about trying to kill the process"
    (let [is-killed (atom true)
          some-ctx (some-ctx-with :result-channel (async/chan 100)
                                  :is-killed is-killed)
          result-channel (:result-channel some-ctx)]
      (bash some-ctx "/" "sleep 5")
      (is (= [[:processed-kill true]]
             (slurp-chan result-channel))))))