(ns lambdacd.ui-core-test
  (:require [cemerick.cljs.test :refer-macros [is are deftest testing use-fixtures done]]
            [lambdacd.ui-core :as core]
            [lambdacd.reagent-testutils :as r]))

(defn found-in [div re]
  (let [res (.-innerHTML div)]
    (if (re-find re res)
      true
      (do (println "Not found: " res)
          false))))


(deftest test-home
  (r/with-mounted-component
    (core/build-history-component
      (atom [{:build-number 1} {:build-number 3}]))
    (testing "that the history contains all the builds"
      (fn [c div]
        (is (found-in div #"Builds"))
        (is (found-in div #"Build 1"))
        (is (found-in div #"Build 3"))))))