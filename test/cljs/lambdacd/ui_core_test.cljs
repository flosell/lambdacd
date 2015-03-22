(ns lambdacd.ui-core-test
  (:require [cemerick.cljs.test :refer-macros [is are deftest testing use-fixtures done]]
            [dommy.core :as dommy]
            [dommy.core :refer-macros [sel sel1]]
            [lambdacd.ui-core :as core]
            [lambdacd.testutils :as tu]))

(defn found-in [div re]
  (let [res (.-innerHTML div)]
    (if (re-find re res)
      true
      (do (println "Not found: " res)
          false))))


(deftest history-test
  (tu/with-mounted-component
    (core/build-history-component
      (atom [{:build-number 1} {:build-number 3}]))
    (testing "that the history contains all the builds"
      (fn [c div]
        (is (found-in div #"Builds"))
        (is (found-in div #"Build 1"))
        (is (found-in div #"Build 3"))))))

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
      (with-children [some-build-step])
      (with-output "hello from p")))

(defn steps [root]
  (sel root :li))

(defn step-label [step]
  (sel1 step :span))

(defn having-class [classname elem]
  (if (dommy/has-class? elem classname)
    true
    (do (println "expected " elem " to have class " classname)
        false)))

(defn containing-ordered-list [elem]
  (not (empty? (sel elem :ol))))
(defn containing-unordered-list [elem]
  (not (empty? (sel elem :ul))))

(defn having-data [name value elem]
  (= value (dommy/attr elem (str "data-" name))))

(defn after-click [atom elem]
  (tu/fire! elem :click)
  @atom)


(deftest pipeline-view-test
  (testing "rendering of a single build-step"
    (let [output-atom (atom "")]
      (tu/with-mounted-component
        (core/build-step-component some-build-step output-atom 1)
        (fn [c div]
          (is (found-in div #"some-step"))
          (is (having-class "build-step" (step-label (first (steps div)))))
          (is (having-data "status" "success" (first (steps div))))
          (is (= "hello world" (after-click output-atom (step-label (first (steps div))))))))))
  (testing "rendering of a container build-step"
    (let [output-atom (atom "")]
      (tu/with-mounted-component
        (core/build-step-component some-container-build-step output-atom 1)
        (fn [c div]
          (is (found-in div #"some-container"))
          (is (found-in (first (steps div)) #"some-step"))
          (is (having-class "build-step" (step-label (first (steps div)))))
          (is (having-data "status" "success" (first (steps div))))
          (is (containing-ordered-list (first (steps div))))
          (is (= "hello from container" (after-click output-atom (step-label (first (steps div))))))))))
  (testing "rendering of a parallel build-step"
    (let [output-atom (atom "")]
      (tu/with-mounted-component
        (core/build-step-component some-parallel-build-step output-atom 1)
        (fn [c div]
          (is (found-in div #"some-parallel-step"))
          (is (found-in (first (steps div)) #"some-step"))
          (is (having-class "build-step" (step-label (first (steps div)))))
          (is (having-data "status" "success" (first (steps div))))
          (is (containing-unordered-list (first (steps div))))
          (is (= "hello from p" (after-click output-atom (step-label (first (steps div)))))))))))