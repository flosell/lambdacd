(ns lambdacd.presentation.unified-test
  (:use [lambdacd.steps.control-flow])
  (:require [clojure.test :refer :all]
            [lambdacd.presentation.unified :refer :all]))
(defn do-stuff [] {})
(defn do-other-stuff [] {})
(defn do-more-stuff [] {})
(defn do-even-more-stuff [] {})

(def foo-pipeline
  `((in-parallel
      (in-cwd do-stuff)
      (in-cwd do-other-stuff))))


(def foo-pipeline-build-state
  {'(1) { :status :running}
   '(1 1 1) {:status :failure
           :out "do stuff failed"}
   '(1 2 1) {:status :running
             :some-key :some-value}})

(def expected-unified-foo-pipeline-presentation
  [{:name "in-parallel"
   :type :parallel
   :step-id '(1)
   :has-dependencies false
   :result {:status :running }
   :children
   [{:name "in-cwd"
     :type :container
     :step-id '(1 1)
     :has-dependencies false
     :result {}
     :children [{:name "do-stuff"
                 :type :step
                 :step-id '(1 1 1)
                 :has-dependencies false
                 :children []
                 :result {:status :failure
                          :out "do stuff failed"}}]}
    {:name "in-cwd"
     :type :container
     :step-id '(2 1)
     :has-dependencies false
     :result {}
     :children [{:name "do-other-stuff"
                 :type :step
                 :step-id '(1 2 1)
                 :has-dependencies false
                 :children []
                 :result {:status :running :some-key :some-value}}]}]}])

(deftest unified-presentation-test
  (testing "that we can merge structure and state to a unified view on a pipeline-run"
    (is (= expected-unified-foo-pipeline-presentation (unified-presentation foo-pipeline foo-pipeline-build-state)))))