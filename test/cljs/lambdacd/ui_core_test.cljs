(ns lambdacd.ui-core-test
  (:require [cemerick.cljs.test :refer-macros [is are deftest testing use-fixtures done]]
            [dommy.core :as dommy]
            [dommy.core :refer-macros [sel sel1]]
            [lambdacd.ui-core :as core]
            [lambdacd.reagent-testutils :as r]))

(defn found-in [div re]
  (let [res (.-innerHTML div)]
    (if (re-find re res)
      true
      (do (println "Not found: " res)
          false))))


(deftest history-test
  (r/with-mounted-component
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

(defn steps [root]
  (sel root :li))

(defn step-label [step]
  (sel1 step :span))

(defn having-class [classname elem]
  (if (dommy/has-class? elem classname)
    true
    (do (println "expected " elem " to have class " classname)
        false)))

(defn having-data [name value elem]
  (= value (dommy/attr elem (str "data-" name))))

(defn fire!
  "Creates an event of type `event-type`, optionally having
   `update-event!` mutate and return an updated event object,
   and fires it on `node`.
   Only works when `node` is in the DOM"
  [node event-type & [update-event!]]
  (let [update-event! (or update-event! identity)]
    (if (.-createEvent js/document)
      (let [event (.createEvent js/document "Event")]
        (.initEvent event (name event-type) true true)
        (.dispatchEvent node (update-event! event)))
      (.fireEvent node (str "on" (name event-type))
                  (update-event! (.createEventObject js/document))))))

(defn after-click [atom elem]
  (fire! elem :click)
  @atom)


(deftest pipeline-view-test
  (testing "rendering of a single build-step"
    (let [output-atom (atom "")]
      (r/with-mounted-component
        (core/build-step-component some-build-step output-atom 1)
        (fn [c div]
          (is (found-in div #"some-step"))
          (is (= 1 (count (steps div))))
          (is (having-class "build-step" (step-label (first (steps div)))))
          (is (having-data "status" "success" (first (steps div))))
          (is (= "hello world" (after-click output-atom (step-label (first (steps div)))))))))))