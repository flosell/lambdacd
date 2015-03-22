(ns lambdacd.route-test
  (:require [cemerick.cljs.test :refer-macros [is are deftest testing use-fixtures done]]
            [dommy.core :refer-macros [sel sel1]]
            [lambdacd.route :as route]))


(deftest dispatch-route-test
  (testing "that an route with a build-number sets the build-number correctly"
    (let [build-number-atom (atom nil)
          step-id-to-display (atom nil)]
      (is (= { :routing :ok } (route/dispatch-route build-number-atom step-id-to-display "/builds/3")))
      (is (= 3 @build-number-atom))))
  (testing "that an route with a build-number sets the displayed step-id back to nil"
           (let [build-number-atom (atom nil)
                 step-id-to-display (atom "something")]
             (route/dispatch-route build-number-atom step-id-to-display "/builds/3")
             (is (= nil @step-id-to-display))))
  (testing "that an route with a build-number and step-id sets both"
           (let [build-number-atom (atom nil)
                 step-id-to-display (atom "something")]
             (is (= { :routing :ok } (route/dispatch-route build-number-atom step-id-to-display "/builds/3/2-1-3")))
             (is (= 3 @build-number-atom))
             (is (= [2 1 3] @step-id-to-display))))
  (testing "that an invalid route leaves the atom alone and returns a path to redirect to"
    (let [build-number-atom (atom nil)
          step-id-to-display (atom nil)]
      (is (= {:routing :failed :redirect-to "/builds/1" } (route/dispatch-route build-number-atom step-id-to-display "/i/dont/know")))
      (is (= nil @build-number-atom)))))


(deftest build-route
  (testing "that we can create a decent route to a build"
    (is (= "#/builds/42" (route/for-build-number 42))))
  (testing "that we can create a route pointing to a particular step and build"
    (is (= "#/builds/42/3-2-1" (route/for-build-and-step-id 42 [3 2 1])))))