(ns lambdacd.output-test
  (:require [cljs.test :refer-macros [deftest is testing run-tests]]
            [lambdacd.dom-utils :as dom]
            [lambdacd.db :as db]
            [dommy.core :refer-macros [sel sel1]]
            [lambdacd.testutils :as tu]
            [re-frame.core :as re-frame]
            [lambdacd.output :as output]))

(def some-child
  {:name "do-other-stuff"
   :step-id [1 1]
   :result {:status "running" :some-key :some-value :out "hello from child"}
   :children []})

(def root-step
  {:name "in-parallel"
   :step-id [1]
   :result {:status "running" :out "hello from root"}
   :children []})

(def some-running-build-state root-step)

(def some-waiting-build-state [{:name "do-other-stuff"
                                   :step-id [1 1]
                                   :result {:status "waiting" :out "waiting..."}
                                   :children []}])
(def some-unknown-build-state [{:name "do-other-stuff"
                                :step-id [1 1]
                                :result {:status nil :out "waiting..."}
                                :children []}])

(def some-build-state-without-out [{:name "do-other-stuff"
                                    :step-id [1 1]
                                    :result {:status :success}
                                    :children []}])

(def some-successful-build-state {:name "do-other-stuff"
                                 :step-id [1 1]
                                 :result {:status "success" :out "hello from successful step"}
                                 :children []})
(def some-successful-build-state-with-details {:name "do-other-stuff"
                                                :step-id [1 1]
                                                :result {:status "success"
                                                         :details [{:label "some details"
                                                                    :href "http://some-url.com"
                                                                    :details [{:label "some nested details"}]}]}
                                                :children []})

(def some-failed-build-state
  {:name     "do-other-stuff"
   :step-id  [1 1]
   :result   {:status "failure" :out "hello from successful child"}
   :children []})
(def some-build-state-that-received-a-kill
  {:name     "do-other-stuff"
   :step-id  [1 1]
   :result   {:status "running" :out "hello" :received-kill true}
   :children []})
(def some-build-state-that-received-a-kill-and-is-dead
  {:name     "do-other-stuff"
   :step-id  [1 1]
   :result   {:status "killed" :out "hello" :received-kill true}
   :children []})
(def some-build-state-that-processed-a-kill
  {:name     "do-other-stuff"
   :step-id  [1 1]
   :result   {:status "running" :out "hello" :processed-kill true :received-kill true}
   :children []})
(def some-build-state-that-processed-a-kill-and-is-dead
  {:name     "do-other-stuff"
   :step-id  [1 1]
   :result   {:status "killed" :out "hello" :processed-kill true :received-kill true}
   :children []})


(def raw-step-result-invisible false)
(def raw-step-result-visible true)

(deftest output-test
  (testing "that a help message is shown when no step selected"
    (tu/with-mounted-component
      (output/output-renderer nil raw-step-result-invisible)
      (fn [c div]
        (is (dom/found-in div #"to display details")))))
  (testing "that we can display the :out output of a step"
    (tu/with-mounted-component
      (output/output-renderer some-child raw-step-result-invisible)
      (fn [c div]
        (is (dom/found-in div #"hello from child")))))
  (testing "that the output contains a message indicating the success of a build step"
    (tu/with-mounted-component
      (output/output-renderer some-successful-build-state raw-step-result-invisible)
      (fn [c div]
        (is (dom/found-in div #"Step is finished: SUCCESS")))))
  (testing "that the output contains a message indicating the failure of a build step"
    (tu/with-mounted-component
      (output/output-renderer some-failed-build-state raw-step-result-invisible)
      (fn [c div]
        (is (dom/found-in div #"Step is finished: FAILURE")))))
  (testing "that the output contains a message indicating that a kill was received"
    (tu/with-mounted-component
      (output/output-renderer some-build-state-that-received-a-kill raw-step-result-invisible)
      (fn [c div]
        (is (dom/found-in div #"LambdaCD received kill")))))
  (testing "that the output contains a message indicating that a kill was processed"
    (tu/with-mounted-component
      (output/output-renderer some-build-state-that-processed-a-kill raw-step-result-invisible)
      (fn [c div]
        (is (dom/found-in div #"Step received kill")))))
  (testing "that the output contains no message indicating that a kill was processed after the step is dead"
    (tu/with-mounted-component
      (output/output-renderer some-build-state-that-processed-a-kill-and-is-dead raw-step-result-invisible)
      (fn [c div]
        (is (dom/not-found-in div #"Step received kill")))))
  (testing "that the output contains no message indicating that a kill was received after the step is dead"
    (tu/with-mounted-component
      (output/output-renderer some-build-state-that-received-a-kill-and-is-dead raw-step-result-invisible)
      (fn [c div]
        (is (dom/not-found-in div #"LambdaCD received kill")))))
  (testing "that the finished message does not appear if the step doesn't have a known state"
    (tu/with-mounted-component
      (output/output-renderer some-unknown-build-state [1 1])
      (fn [c div]
        (is (dom/not-found-in div #"Step is finished"))))
    (tu/with-mounted-component
      (output/output-renderer some-waiting-build-state [1 1])
      (fn [c div]
        (is (dom/not-found-in div #"Step is finished")))))
  (testing "that the console output does not appear if no :out is there"
    (tu/with-mounted-component
      (output/output-renderer some-build-state-without-out [1 1])
      (fn [c div]
        (is (dom/not-found-in div #"Output")))))
  (testing "that the finished message does not appear if the step is still running"
    (tu/with-mounted-component
      (output/output-renderer some-running-build-state [1 1])
      (fn [c div]
        (is (dom/not-found-in div #"Step is finished"))))
    (tu/with-mounted-component
      (output/output-renderer some-waiting-build-state [1 1])
      (fn [c div]
        (is (dom/not-found-in div #"Step is finished")))))
  (testing "that we can display the other attributes of the output map"
    (tu/with-mounted-component
      (output/output-renderer some-child raw-step-result-visible)
      (fn [c div]
        (is (dom/found-in (sel1 div :button) #"hide"))
        (is (dom/found-in div #"some-key"))
        (is (dom/found-in div #"some-value")))))
  (testing "that we can hide the other attributes of the output map"
    (tu/with-mounted-component
      (output/output-renderer some-child raw-step-result-invisible)
      (fn [c div]
        (dom/fire! (sel1 div :button) :click)
        (is (dom/found-in (sel1 div :button) #"show"))
        (is (dom/not-found-in div #"some-key"))
        (is (dom/not-found-in div #"some-value")))))
  (testing "that the details are displayed if present"
    (tu/with-mounted-component
      (output/output-renderer some-successful-build-state-with-details raw-step-result-visible)
      (fn [c div]
        (is (dom/found-in div #"some details"))
        (is (dom/containing-link-to div "http://some-url.com"))
        (is (dom/found-in div #"nested details"))))))