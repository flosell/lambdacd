(ns lambdacd.pipeline-test
  (:require [cljs.test :refer-macros [deftest is testing run-tests]]
            [lambdacd.testdata :refer [some-build-step some-build-step-id with-name with-type with-output with-children time-start time-after-ten-sec]]
            [dommy.core :as dommy]
            [dommy.core :refer-macros [sel sel1]]
            [lambdacd.pipeline :as pipeline]
            [lambdacd.dom-utils :as dom]
            [lambdacd.testutils :as tu]
            [lambdacd.route :as route]
            [lambdacd.db :as db]
            [re-frame.core :as re-frame]))

(def some-other-step
  (-> some-build-step
      (with-name "some-other-step")))

(def some-container-build-step
  (-> some-build-step
      (with-name "some-container")
      (with-type "container")
      (with-children [some-build-step])
      (with-output "hello from container")))

(def some-parallel-build-step
  (-> some-container-build-step
      (with-name "some-parallel-step")
      (with-type "parallel")
      (with-children [some-other-step])
      (with-output "hello from p")))

(defn steps [root]
  (sel root :li))

(defn step-label [step]
  (sel1 step :span))

(def some-step-id-to-display 10)

(deftest build-step-duration-formatter-test
  (testing "that it formats the duration of a build step"
    (is (= "00:10" (pipeline/format-build-step-duration {:status                :success
                                                         :first-updated-at      time-start
                                                         :most-recent-update-at time-after-ten-sec}))))
  (testing "that we don't format waiting steps"
    (is (= "" (pipeline/format-build-step-duration {:status                :success
                                                    :first-updated-at      time-start
                                                    :has-been-waiting      true
                                                    :most-recent-update-at time-after-ten-sec}))))
  (testing "that we don't format steps that didn't run yet"
    (is (= "" (pipeline/format-build-step-duration {:status                nil
                                                    :first-updated-at      time-start
                                                    :most-recent-update-at time-after-ten-sec})))))

(defn subscription-stub [x]
  (atom
    (case x
      [::db/build-number]   1
      [::db/step-id]        42
      [::db/pipeline-state] [some-parallel-build-step]
      [::db/step-expanded? some-build-step-id] true
      [::db/all-collapsed?] false
      [::db/all-expanded?] false
      [::db/expand-active-active?] false
      [::db/expand-failures-active?] false
      (do
        (println "ERROR: " x " not mocked")
        (throw "mock missing")))))

(deftest build-step-test
  (with-redefs [re-frame/subscribe subscription-stub]
    (testing "rendering of a single build-step"
      (tu/with-mounted-component
        [pipeline/build-step-component some-build-step some-step-id-to-display]
        (fn [c div]
          (is (dom/found-in div #"some-step"))
          (is (dom/having-class "build-step" (step-label (first (steps div)))))
          (is (dom/having-data "status" "success" (first (steps div))))
          (is (dom/containing-link-to div (route/for-build-and-step-id 1 [1 2 3]))))))
    (testing "rendering of a container build-step"
      (tu/with-mounted-component
        [pipeline/build-step-component some-container-build-step some-step-id-to-display]
        (fn [c div]
          (is (dom/found-in div #"some-container"))
          (is (dom/found-in (first (steps div)) #"some-step"))
          (is (dom/having-class "build-step" (step-label (first (steps div)))))
          (is (dom/having-data "status" "success" (first (steps div))))
          (is (dom/containing-ordered-list (first (steps div)))))))
    (testing "rendering of a parallel build-step"
      (tu/with-mounted-component
        [pipeline/build-step-component some-parallel-build-step some-step-id-to-display]
        (fn [c div]
          (is (dom/found-in div #"some-parallel-step"))
          (is (dom/found-in (first (steps div)) #"some-other-step"))
          (is (dom/having-class "build-step" (step-label (first (steps div)))))
          (is (dom/having-data "status" "success" (first (steps div))))
          (is (dom/containing-unordered-list (first (steps div)))))))))

(deftest pipeline-test
  (testing "rendering of a complete pipeline"
    (with-redefs [re-frame/subscribe subscription-stub]
      (tu/with-mounted-component
        [pipeline/pipeline-component]
        (fn [c div]
          (is (dom/found-in div #"some-parallel-step"))
          (is (dom/found-in div #"some-other-step")))))))
