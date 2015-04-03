(ns lambdacd.output-test
  (:require [cemerick.cljs.test :refer-macros [is are deftest testing use-fixtures done]]
            [lambdacd.dom-utils :as dom]
            [dommy.core :refer-macros [sel sel1]]
            [lambdacd.testutils :as tu]
            [lambdacd.output :as output]))

(def some-child
  {:name "do-other-stuff"
   :step-id [1 1]
   :result {:status :running :some-key :some-value :out "hello from child"}
   :children: []})

(def root-step
  {:name "in-parallel"
   :step-id [1]
   :result {:status :ok :out "hello from root"}
   :children
   [some-child]})

(def some-build-state [root-step])

(deftest output-test
         (testing "that we can display the :out output of a step"
                  (tu/with-mounted-component
                    (output/output-component some-build-state [1 1] (atom true))
                    (fn [c div]
                      (is (dom/found-in div #"hello from child")))))
         (testing "that we can display the other attributes of the output map"
                  (tu/with-mounted-component
                    (output/output-component some-build-state [1 1] (atom true))
                    (fn [c div]
                      (is (dom/found-in (sel1 div :button) #"-"))
                      (is (dom/found-in div #"some-key"))
                      (is (dom/found-in div #"some-value")))))
         (testing "that we can hide the other attributes of the output map"
                  (tu/with-mounted-component
                    (output/output-component some-build-state [1 1] (atom false))
                    (fn [c div]
                      (dom/fire! (sel1 div :button) :click)
                      (is (dom/found-in (sel1 div :button) #"\+"))
                      (is (dom/not-found-in div #"some-key"))
                      (is (dom/not-found-in div #"some-value"))))))