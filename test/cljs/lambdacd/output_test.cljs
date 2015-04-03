(ns lambdacd.output-test
  (:require [cemerick.cljs.test :refer-macros [is are deftest testing use-fixtures done]]
            [lambdacd.dom-utils :as dom]
            [dommy.core :refer-macros [sel sel1]]
            [lambdacd.testutils :as tu]
            [lambdacd.output :as output]
            [lambdacd.pipeline :as pipeline]))

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
         (testing "rendering of a complete pipeline"
                  (tu/with-mounted-component
                    (output/output-component some-build-state [1 1])
                    (fn [c div]
                      (is (dom/found-in div #"hello from child"))))))