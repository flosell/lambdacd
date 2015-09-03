(ns lambdacd.pipeline-test
  (:require [cemerick.cljs.test :refer-macros [is are deftest testing use-fixtures done]]
            [dommy.core :as dommy]
            [dommy.core :refer-macros [sel sel1]]
            [lambdacd.pipeline :as pipeline]
            [lambdacd.dom-utils :as dom]
            [lambdacd.testutils :as tu]
            [lambdacd.route :as route]))

(def some-build-step
  {:name "some-step"
   :type "step"
   :step-id [1 2 3]
   :children []
   :result {:status "success"
            :out "hello world"}})

(defn with-name [step name]
  (assoc step :name name))

(defn with-type [step name]
  (assoc step :type name))

(defn with-output [step output]
  (assoc step :result {:status "success" :out output}))

(defn with-children [step children]
  (assoc step :children children))

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

(deftest build-step-test
  (testing "rendering of a single build-step"
    (tu/with-mounted-component
      (pipeline/build-step-component some-build-step 1 some-step-id-to-display)
      (fn [c div]
        (is (dom/found-in div #"some-step"))
        (is (dom/having-class "build-step" (step-label (first (steps div)))))
        (is (dom/having-data "status" "success" (first (steps div))))
        (is (dom/containing-link-to div (route/for-build-and-step-id 1 [1 2 3]))))))
  (testing "rendering of a container build-step"
    (tu/with-mounted-component
      (pipeline/build-step-component some-container-build-step 1 some-step-id-to-display)
      (fn [c div]
        (is (dom/found-in div #"some-container"))
        (is (dom/found-in (first (steps div)) #"some-step"))
        (is (dom/having-class "build-step" (step-label (first (steps div)))))
        (is (dom/having-data "status" "success" (first (steps div))))
        (is (dom/containing-ordered-list (first (steps div)))))))
  (testing "rendering of a parallel build-step"
    (tu/with-mounted-component
      (pipeline/build-step-component some-parallel-build-step 1 some-step-id-to-display)
      (fn [c div]
        (is (dom/found-in div #"some-parallel-step"))
        (is (dom/found-in (first (steps div)) #"some-other-step"))
        (is (dom/having-class "build-step" (step-label (first (steps div)))))
        (is (dom/having-data "status" "success" (first (steps div))))
        (is (dom/containing-unordered-list (first (steps div))))))))


(deftest pipeline-test
  (testing "rendering of a complete pipeline"
    (tu/with-mounted-component
      (pipeline/pipeline-component 1 (atom [some-parallel-build-step]) 42)
      (fn [c div]
        (is (dom/found-in div #"some-parallel-step"))
        (is (dom/found-in div #"some-other-step"))))))
