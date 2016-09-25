(ns lambdacd.presentation.unified-test
  (:require [clojure.test :refer :all]
            [lambdacd.presentation.unified :refer :all]
            [lambdacd.steps.control-flow :refer [in-parallel in-cwd]]))

(defn do-stuff [] {})
(defn do-other-stuff [] {})

(def foo-pipeline
  `((in-parallel
      (in-cwd do-stuff)
      (in-cwd do-other-stuff))))


(def foo-pipeline-build-state
  {'(1)     {:status :running}
   '(1 1 1) {:status :failure
             :out    "do stuff failed"}
   '(1 2 1) {:status   :running
             :some-key :some-value}})

(def expected-unified-foo-pipeline-presentation
  [{:name             "in-parallel"
    :type             :parallel
    :step-id          '(1)
    :has-dependencies false
    :result           {:status :running}
    :children
                      [{:name             "in-cwd"
                        :type             :container
                        :step-id          '(1 1)
                        :has-dependencies false
                        :result           {}
                        :children         [{:name             "do-stuff"
                                            :type             :step
                                            :step-id          '(1 1 1)
                                            :has-dependencies false
                                            :children         []
                                            :result           {:status :failure
                                                               :out    "do stuff failed"}}]}
                       {:name             "in-cwd"
                        :type             :container
                        :step-id          '(2 1)
                        :has-dependencies false
                        :result           {}
                        :children         [{:name             "do-other-stuff"
                                            :type             :step
                                            :step-id          '(1 2 1)
                                            :has-dependencies false
                                            :children         []
                                            :result           {:status :running :some-key :some-value}}]}]}])

(def foo-pipeline-structure
  [{:name             "in-parallel"
    :type             :parallel
    :step-id          '(1)
    :has-dependencies false
    :children
                      [{:name             "in-cwd"
                        :type             :container
                        :step-id          '(1 1)
                        :has-dependencies false
                        :children         [{:name             "do-stuff"
                                            :type             :step
                                            :step-id          '(1 1 1)
                                            :has-dependencies false
                                            :children         []}]}
                       {:name             "in-cwd"
                        :type             :container
                        :step-id          '(2 1)
                        :has-dependencies false
                        :result           {}
                        :children         [{:name             "do-other-stuff"
                                            :type             :step
                                            :step-id          '(1 2 1)
                                            :has-dependencies false
                                            :children         []}]}]}])

(deftest unified-presentation-test
  (testing "that we can merge structure and state to a unified view on a pipeline-run"
    (testing "deprecated call with pipeline-def"
      (is (= expected-unified-foo-pipeline-presentation (unified-presentation foo-pipeline foo-pipeline-build-state))))
    (testing "call with build-result"
      (is (= expected-unified-foo-pipeline-presentation (pipeline-structure-with-step-results foo-pipeline-structure foo-pipeline-build-state))))))
