(ns lambdacd.test-util-test
  (:require [clojure.test :refer :all]
            [lambdacd.test-util :refer :all]
            [clojure.core.async :as async]))

(defn some-function-changing-an-atom [a]
  (reset! a "hello")
  (reset! a "world"))

(defn some-step-taking-50ms [arg & _]
  (Thread/sleep 50)
  {:foo :bar})


(deftest atom-history-test
  (testing "that we can record the history of an atom"
    (let [some-atom (atom "")]
      (is (= ["hello" "world"]
             (atom-history-for some-atom (some-function-changing-an-atom some-atom)))))))

(deftest history-for-test
  (testing "that we can record the history of an atom"
    (let [some-atom (atom "")
          history-atom (history-for some-atom)]
      (some-function-changing-an-atom some-atom)
      (is (= ["hello" "world"]
             @history-atom)))))


(deftest result-channel->map-test
  (testing "that it converts the channel-data to a map"
    (is (= {} (result-channel->map (async/to-chan []))))
    (is (= {:status :success} (result-channel->map (async/to-chan [[:status :success]]))))
    (is (= {:status :success :out "hello world"}
           (result-channel->map (async/to-chan [[:out "hello"] [:out "hello world"] [:status :success] ]))))
    (is (= {:status :success :out "hello world"}
           (result-channel->map (async/to-chan [[:out "hello"] [:out "hello world"] [:status :success] [:foo :bar] ]))))))

(deftest timing-test
  (testing "that my-time more or less accurately measures the execution time of a step"
    (is (close? 10 50 (my-time (some-step-taking-50ms {}))))))