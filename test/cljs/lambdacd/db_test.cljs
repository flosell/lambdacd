(ns lambdacd.db-test
  (:require-macros
    [cemerick.cljs.test :refer [is are deftest testing use-fixtures done]])
  (:require
    [reagent.core :as r]
    [lambdacd.db :as db]))

(deftest get-set-history-test
         (testing "that we can set and update history"
                  (let [db (r/atom db/default-db)]
                    (reset! db (db/history-updated-handler @db [nil [:some :history]]))
                    (is (= [:some :history] @(db/history-subscription db nil))))))

(deftest get-set-pipeline-state-test
         (testing "that we can set and update history"
                  (let [db (r/atom db/default-db)]
                    (reset! db (db/pipeline-state-updated-handler @db [nil [:some :state]]))
                    (is (= [:some :state] @(db/pipeline-state-subscription db nil))))))

(deftest lost-connection-test
         (testing "that initially there is no connection"
                  (let [db (r/atom db/default-db)]
                    (is (= :lost @(db/connection-state-subscription db nil)))))
         (testing "that updated state restores the connection state"
                  (let [db (r/atom db/default-db)]
                    (reset! db (db/pipeline-state-updated-handler @db [nil [:some :state]]))
                    (is (= :active @(db/connection-state-subscription db nil)))))
         (testing "that updated history restores the connection state"
                  (let [db (r/atom db/default-db)]
                    (reset! db (db/history-updated-handler @db [nil [:some :state]]))
                    (is (= :active @(db/connection-state-subscription db nil)))))
         (testing "that we can notify about lost connection"
                  (let [db (r/atom db/default-db)]
                    ; first, set it active
                    (reset! db (db/history-updated-handler @db [nil [:some :state]]))
                    (is (= :active @(db/connection-state-subscription db nil)))
                    ; then loose connection
                    (reset! db (db/lost-connection-handler @db [nil]))
                    (is (= :lost @(db/connection-state-subscription db nil))))))