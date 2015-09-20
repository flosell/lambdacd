(ns lambdacd.db-test
  (:require-macros
    [cemerick.cljs.test :refer [is are deftest testing use-fixtures done]])
  (:require
    [reagent.core :as r]
    [lambdacd.db :as db]))

(deftest get-set-history-test
         (testing "that we can set and update history"
                  (let [db (r/atom {})]
                    (reset! db (db/history-updated-handler @db [nil [:some :history]]))
                    (is (= [:some :history] @(db/history-subscription db nil))))))

(deftest get-set-pipeline-state-test
         (testing "that we can set and update history"
                  (let [db (r/atom {})]
                    (reset! db (db/pipeline-state-updated-handler @db [nil [:some :state]]))
                    (is (= [:some :state] @(db/pipeline-state-subscription db nil))))))
