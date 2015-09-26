(ns lambdacd.history-test
  (:require [cljs.test :refer-macros [deftest is testing run-tests]]
            [lambdacd.dom-utils :as dom]
            [dommy.core :refer-macros [sel sel1]]
            [lambdacd.history :as history]
            [lambdacd.testutils :as tu]))

(deftest history-test-cljs
  (testing "that the history contains all the builds"
           (tu/with-mounted-component
             [:div (history/build-history-component
               [{:build-number 1} {:build-number 3}] 1)]
               (fn [c div]
                 (is (dom/found-in div #"Builds"))
                 (is (dom/found-in div #"Build 1"))
                 (is (dom/found-in div #"Build 3")))))
  (testing "that the history contains all the builds"
           (tu/with-mounted-component
             [:div (history/build-history-component
               [{:build-number 1} {:build-number 3}] 1)]
             (fn [c div]
               (is (dom/found-in div #"Builds"))
               (is (dom/found-in div #"Build 1"))
               (is (dom/found-in div #"Build 3")))))
  (testing "that the history displays the duration of a build"
           (tu/with-mounted-component
             [:div (history/build-history-component
               [{:build-number 1
                 :first-updated-at "2015-05-17T10:02:36.684Z"
                 :most-recent-update-at "2015-05-17T10:03:51.684Z"}] 1)]
             (fn [c div]
               (is (dom/found-in div #"1min 15sec")))))
  (testing "that the history displays build status icons"
           (tu/with-mounted-component
             [:div (history/build-history-component
               [{:build-number 1 :status "some-status-not-known"}
                {:build-number 2 :status "failure"}
                {:build-number 3 :status "success"}
                {:build-number 4 :status "running"}
                {:build-number 5 :status "waiting"}] 1)]
             (fn [c div]
               (is (dom/found-in div #"fa-question"))
               (is (dom/found-in div #"fa-times"))
               (is (dom/found-in div #"fa-check"))
               (is (dom/found-in div #"fa-cog"))
               (is (dom/found-in div #"fa-pause")))))
  (testing "that we render a loading-screen if no history is definde"
           (tu/with-mounted-component
             [:div
              (history/build-history-component nil 1)]
              (fn [c div]
                 (is (dom/found-in div #"Builds"))
                 (is (dom/found-in div #"Loading"))))))
