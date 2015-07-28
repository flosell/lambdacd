(ns lambdacd.output-test
  (:require [cemerick.cljs.test :refer-macros [is are deftest testing use-fixtures done]]
            [lambdacd.dom-utils :as dom]
            [dommy.core :refer-macros [sel sel1]]
            [lambdacd.testutils :as tu]
            [lambdacd.output :as output]))

(def some-child
  {:name "do-other-stuff"
   :step-id [1 1]
   :result {:status "running" :some-key :some-value :out "hello from child"}
   :children: []})

(def root-step
  {:name "in-parallel"
   :step-id [1]
   :result {:status "running" :out "hello from root"}
   :children
   [some-child]})

(def some-running-build-state [root-step])

(def some-waiting-build-state [{:name "do-other-stuff"
                                   :step-id [1 1]
                                   :result {:status "waiting" :out "waiting..."}
                                   :children: []}])
(def some-unknown-build-state [{:name "do-other-stuff"
                                :step-id [1 1]
                                :result {:status nil :out "waiting..."}
                                :children: []}])

(def some-successful-build-state [{:name "do-other-stuff"
                                 :step-id [1 1]
                                 :result {:status "success" :out "hello from successful step"}
                                 :children: []}])
(def some-successful-build-state-with-details [{:name "do-other-stuff"
                                                :step-id [1 1]
                                                :result {:status "success"
                                                         :details [{:label "some details"
                                                                    :href "http://some-url.com"
                                                                    :details [{:label "some nested details"}]}]}
                                                :children: []}])

(def some-failed-build-state [{:name "do-other-stuff"
                                 :step-id [1 1]
                                 :result {:status "failure" :out "hello from successful child"}
                                 :children: []}])

(deftest output-test
         (testing "that a help message is shown when no step selected"
                  (tu/with-mounted-component
                    (output/output-component some-running-build-state nil (atom true))
                    (fn [c div]
                      (is (dom/found-in div #"to display details")))))
         (testing "that we can display the :out output of a step"
                  (tu/with-mounted-component
                    (output/output-component some-running-build-state [1 1] (atom true))
                    (fn [c div]
                      (is (dom/found-in div #"hello from child")))))
         (testing "that the output contains a message indicating the success of a build step"
                  (tu/with-mounted-component
                    (output/output-component some-successful-build-state [1 1] (atom true))
                    (fn [c div]
                      (is (dom/found-in div #"Step is finished: SUCCESS")))))
         (testing "that the output contains a message indicating the failure of a build step"
                  (tu/with-mounted-component
                    (output/output-component some-failed-build-state [1 1] (atom true))
                    (fn [c div]
                      (is (dom/found-in div #"Step is finished: FAILURE")))))
         (testing "that the finished message does not appear if the step doesn't have a known state"
                  (tu/with-mounted-component
                    (output/output-component some-unknown-build-state [1 1] (atom true))
                    (fn [c div]
                      (is (dom/not-found-in div #"Step is finished"))))
                  (tu/with-mounted-component
                    (output/output-component some-waiting-build-state [1 1] (atom true))
                    (fn [c div]
                      (is (dom/not-found-in div #"Step is finished")))))
         (testing "that the finished message does not appear if the step is still running"
                  (tu/with-mounted-component
                    (output/output-component some-running-build-state [1 1] (atom true))
                    (fn [c div]
                      (is (dom/not-found-in div #"Step is finished"))))
                  (tu/with-mounted-component
                    (output/output-component some-waiting-build-state [1 1] (atom true))
                    (fn [c div]
                      (is (dom/not-found-in div #"Step is finished")))))
         (testing "that we can display the other attributes of the output map"
                  (tu/with-mounted-component
                    (output/output-component some-running-build-state [1 1] (atom true))
                    (fn [c div]
                      (is (dom/found-in (sel1 div :button) #"hide"))
                      (is (dom/found-in div #"some-key"))
                      (is (dom/found-in div #"some-value")))))
         (testing "that we can hide the other attributes of the output map"
                  (tu/with-mounted-component
                    (output/output-component some-running-build-state [1 1] (atom false))
                    (fn [c div]
                      (dom/fire! (sel1 div :button) :click)
                      (is (dom/found-in (sel1 div :button) #"show"))
                      (is (dom/not-found-in div #"some-key"))
                      (is (dom/not-found-in div #"some-value")))))
         (testing "that the details are displayed if present"
                  (tu/with-mounted-component
                    (output/output-component some-successful-build-state-with-details [1 1] (atom false))
                    (fn [c div]
                      (is (dom/found-in div #"some details"))
                      (is (dom/containing-link-to div "http://some-url.com"))
                      (is (dom/found-in div #"nested details"))))))