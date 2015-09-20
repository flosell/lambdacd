(ns lambdacd.db-test
  (:require [cemerick.cljs.test :refer-macros [is are deftest testing use-fixtures done]]
            [reagent.core :as r]
            [lambdacd.db :as db]))

(deftest history-updated-handler-test
         (testing "that we can update the history"
                  (let [db { :history [:some :history]}]
                    (is (= { :history [:some :other-history]} (db/history-updated-handler db [nil [:some :other-history]]))))))


(deftest history-subscription-test
         (testing "that we can get the history out"
                  (let [db (r/atom { :history [:some :history]})]
                    (is (= [:some :history] @(db/history-subscription db nil))))))

(deftest state-subscription-test
         (testing "that we can get the state out"
                  (let [db (r/atom { :pipeline-state [:some :state]})]
                    (is (= [:some :state] @(db/pipeline-state-subscription db nil))))))