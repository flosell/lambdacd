(ns lambdacd.handlers-test
  (:require [cemerick.cljs.test :refer-macros [is are deftest testing use-fixtures done]]
            [reagent.core :as r]
            [lambdacd.handlers :as handlers]))

(deftest history-updated-handler-test
         (testing "that we can update the history"
                  (let [db { :history [:some :history]}]
                    (is (= { :history [:some :other-history]} (handlers/history-updated-handler db [nil [:some :other-history]]))))))