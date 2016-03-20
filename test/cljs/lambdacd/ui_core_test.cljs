(ns lambdacd.ui-core-test
  (:require [cljs.test :refer-macros [deftest is testing run-tests]]
            [lambdacd.dom-utils :as dom]
            [dommy.core :refer-macros [sel sel1]]
            [lambdacd.ui-core :as core]
            [re-frame.core :as re-frame]
            [lambdacd.db :as db]
            [lambdacd.testutils :as tu :refer [mock-subscriptions]]))

(deftest current-build-component-test
         (testing "a normally rendered pipeline"
           (with-redefs [re-frame/subscribe (mock-subscriptions {::db/current-step-result {:name "do-other-stuff"
                                                                                          :step-id [0 1 2]
                                                                                          :result {:status "success" :out "hello from successful step"}
                                                                                          :children []}
                                                                 ::db/current-build-number 3
                                                                 ::db/step-id [0 1 2]})]
                  (tu/with-mounted-component
                    [:div
                     (core/wired-current-build-component (atom []) 3)]
                    (fn [c div]
                      (is (dom/found-in div #"Current Build 3"))
                      (is (dom/found-in div #"Output"))))))
         (testing "a pipeline view without data"
           (tu/with-mounted-component
             (core/wired-current-build-component (atom nil) 3)
             (fn [c div]
               (is (dom/found-in div #"Loading..."))))))

