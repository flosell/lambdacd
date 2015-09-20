(ns lambdacd.subs-test
  (:require [cemerick.cljs.test :refer-macros [is are deftest testing use-fixtures done]]
            [reagent.core :as r]
            [lambdacd.subs :as subs]))

(deftest history-subscription-test
         (testing "that we can get the history out"
                  (let [db (r/atom { :history [:some :history]})]
                    (is (= [:some :history] @(subs/history-subscription db nil))))))

(deftest state-subscription-test
         (testing "that we can get the state out"
                  (let [db (r/atom { :pipeline-state [:some :state]})]
                    (is (= [:some :state] @(subs/state-subscription db nil))))))