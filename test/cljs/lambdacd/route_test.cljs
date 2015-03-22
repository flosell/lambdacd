(ns lambdacd.route-test
  (:require [cemerick.cljs.test :refer-macros [is are deftest testing use-fixtures done]]
            [lambdacd.dom-utils :as dom]
            [dommy.core :refer-macros [sel sel1]]
            [lambdacd.route :as route]
            [lambdacd.testutils :as tu]))


(deftest dispatch-route-test
  (testing "that an route with a build-number sets the build-number correctly"
    (let [build-number-atom (atom nil)]
      (is (= { :routing :ok } (route/dispatch-route build-number-atom "/builds/3")))
      (is (= "3" @build-number-atom))))
  (testing "that an invalid route leaves the atom alone and returns a path to redirect to"
    (let [build-number-atom (atom nil)]
      (is (= {:routing :failed :redirect-to "/builds/1" } (route/dispatch-route build-number-atom "/i/dont/know")))
      (is (= nil @build-number-atom)))))


(deftest build-route
  (testing "that we can create a decent route to a build"
    (is (= "#/builds/42" (route/for-build-number 42)))))