(ns lambdacd.route-test
  (:require [cljs.test :refer-macros [deftest is testing run-tests]]
            [dommy.core :refer-macros [sel sel1]]
            [re-frame.core :as re-frame]
            [lambdacd.route :as route]))

; FIXME: clean up mocking!

(deftest dispatch-route-test
  (testing "that a route with a build-number sets the build-number correctly"
    (let [build-number-atom (atom nil)
          step-id-to-display (atom nil)
          state-atom (atom nil)]
      (with-redefs [re-frame/dispatch (fn [[_ build-number]]
                                        (reset! build-number-atom build-number))]
                   (is (= { :routing :ok } (route/dispatch-route step-id-to-display state-atom "/builds/3")))
                   (is (= 3 @build-number-atom)))))
  (testing "that an route with a build-number sets the displayed step-id back to nil"
           (let [step-id-to-display (atom "something")
                 state-atom (atom nil)]
             (route/dispatch-route step-id-to-display state-atom "/builds/3")
             (is (= nil @step-id-to-display))))
  (testing "that an route with a build-number resets the build-state so that we don't get a half-ready display until the new state is loaded"
           (let [step-id-to-display (atom "something")
                 state-atom (atom "some-state")]
             (route/dispatch-route step-id-to-display state-atom "/builds/3")
             (is (= nil @state-atom))))
  (testing "that an route with a build-number and step-id sets both"
           (let [build-number-atom (atom nil)
                 step-id-to-display (atom "something")
                 state-atom (atom nil)]
             (with-redefs [re-frame/dispatch (fn [[_ build-number]]
                                               (reset! build-number-atom build-number))]
               (is (= { :routing :ok } (route/dispatch-route step-id-to-display state-atom "/builds/3/2-1-3")))
               (is (= 3 @build-number-atom))
               (is (= [2 1 3] @step-id-to-display)))))
  (testing "that an invalid route leaves the atom alone and returns a path to redirect to"
    (let [build-number-atom (atom nil)
          step-id-to-display (atom nil)
          state-atom (atom nil)]
      (is (= {:routing :failed } (route/dispatch-route step-id-to-display state-atom "/i/dont/know")))
      (is (= nil @build-number-atom)))))


(deftest build-route
  (testing "that we can create a decent route to a build"
    (is (= "#/builds/42" (route/for-build-number 42))))
  (testing "that we can create a route pointing to a particular step and build"
    (is (= "#/builds/42/3-2-1" (route/for-build-and-step-id 42 [3 2 1])))))