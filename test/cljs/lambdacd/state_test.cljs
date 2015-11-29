(ns lambdacd.state-test
  (:require [cljs.test :refer-macros [deftest is testing run-tests]]
            [dommy.core :refer-macros [sel sel1]]
            [lambdacd.testdata :refer [some-build-step with-step-id with-status with-children]]
            [lambdacd.state :as state]))

(def cwd-child-a
  {:name "do-stuff"
   :step-id [1 1 1]
   :children []})
(def cwd-child-b
  {:name "do-other-stuff"
   :step-id [1 2 1]
   :result {:status :running :some-key :some-value}
   :children []})

(def parallel-child-a
  {:name "in-cwd"
   :step-id [1 1]
   :children [cwd-child-a]})

(def parallel-child-b
  {:name "in-cwd"
   :step-id [2 1]
   :children [cwd-child-b]})

(def root-step
  {:name "in-parallel"
   :step-id [1]
   :children
   [parallel-child-a
    parallel-child-b]})
(def root-step2
  {:name "some-step"
   :step-id [2]
   :children
   []})

(def step-with-running-children
  (-> some-build-step
      (with-step-id [1])
      (with-status "running")
      (with-children [(-> some-build-step
                          (with-step-id [1 1])
                          (with-status "failure"))
                      (-> some-build-step
                          (with-step-id [2 1])
                          (with-status "running"))])))
(def waiting-step
  (-> some-build-step
      (with-step-id [1])
      (with-status "waiting")))

(def some-pipeline-state [root-step root-step2])

(def flattened-pipeline-state
  [root-step parallel-child-a cwd-child-a parallel-child-b cwd-child-b root-step2])

(deftest flatten-test
  (testing "that we can flatten a pipeline-state-representation"
    (is (= flattened-pipeline-state (into [] (state/flatten-state some-pipeline-state))))))

(deftest get-by-step-id-test
 (testing "that we can find a step by it's id even if it's nested"
          (is (= cwd-child-b (state/find-by-step-id some-pipeline-state [1 2 1])))))

(deftest is-active-test
  (testing "that running steps are active"
    (is (= true (state/is-active? (-> some-build-step
                                      (with-status "running"))))))
  (testing "that waiting steps are active"
    (is (= true (state/is-active? (-> some-build-step
                                      (with-status "waiting"))))))
  (testing "that finished steps are inactive"
    (is (= false (state/is-active? (-> some-build-step
                                      (with-status "success")))))
    (is (= false (state/is-active? (-> some-build-step
                                      (with-status "failure")))))
    (is (= false (state/is-active? (-> some-build-step
                                      (with-status "killed"))))))
  (testing "that steps with undefined state are inactive"
    (is (= false (state/is-active? (-> some-build-step
                                      (with-status nil)))))))