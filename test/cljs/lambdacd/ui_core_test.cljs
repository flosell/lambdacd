(ns lambdacd.ui-core-test
  (:require [cemerick.cljs.test :refer-macros [is are deftest testing use-fixtures done]]
            [lambdacd.dom-utils :as dom]
            [dommy.core :refer-macros [sel sel1]]
            [lambdacd.ui-core :as core]
            [lambdacd.testutils :as tu]))




(deftest history-test
  (tu/with-mounted-component
    (core/build-history-component
      (atom [{:build-number 1} {:build-number 3}]))
    (testing "that the history contains all the builds"
      (fn [c div]
        (is (dom/found-in div #"Builds"))
        (is (dom/found-in div #"Build 1"))
        (is (dom/found-in div #"Build 3"))))))
