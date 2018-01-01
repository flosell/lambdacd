(ns lambdacd.state.internal.dead-steps-marking-test
  (:require [clojure.test :refer :all]
            [lambdacd.state.internal.dead-steps-marking :refer :all]
            [lambdacd.testsupport.data :refer [some-ctx-with]]))

(deftest mark-dead-steps-test
  (testing "that active steps that don't show up in active build step tracking are marked as dead"
    (let [ctx (some-ctx-with :started-steps (atom #{{:step-id      [2]
                                                     :build-number 1}}))]
      (is (= {[1] {:status :dead}
              [2] {:status :running}}
             (mark-dead-steps ctx 1 {[1] {:status :running}
                                     [2] {:status :running}})))
      (is (= {[1] {:status :dead}
              [2] {:status :dead}}
             (mark-dead-steps ctx 2 {[1] {:status :running}
                                     [2] {:status :running}})))))
  (testing "that inactive steps that don't show up in active build step tracking are left as they were"
    (let [ctx (some-ctx-with :started-steps (atom #{}))]
      (is (= {[1] {:status :success}
              [2] {:status :failure}}
             (mark-dead-steps ctx 1 {[1] {:status :success}
                                     [2] {:status :failure}})))))
  (testing "that it preserves incoming nils"
    (let [ctx (some-ctx-with :started-steps (atom #{}))]
      (is (= nil (mark-dead-steps ctx 1 nil))))))
