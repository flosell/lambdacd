(ns lambdacd.ui-core-test
  (:require [cemerick.cljs.test :refer-macros [is are deftest testing use-fixtures done]]
            [lambdacd.dom-utils :as dom]
            [dommy.core :refer-macros [sel sel1]]
            [lambdacd.ui-core :as core]
            [lambdacd.testutils :as tu]))

(deftest current-build-component-test
         (testing "a normally rendered pipeline"
                  (tu/with-mounted-component
                    (core/wired-current-build-component (atom [{:name "do-other-stuff"
                                                                :step-id [0 1 2]
                                                                :result {:status "success" :out "hello from successful step"}
                                                                :children: []}]) 3 (atom [0 1 2]) (atom false))
                    (fn [c div]
                      (is (dom/found-in div #"Current Build 3"))
                      (is (dom/found-in div #"Output")))))
         (testing "a pipeline view without data"
         (tu/with-mounted-component
           (core/wired-current-build-component (atom nil) 3 (atom [0 1 2]) (atom false))
           (fn [c div]
             (is (dom/found-in div #"Loading..."))))))

