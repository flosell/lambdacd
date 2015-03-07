(ns lambdacd.presentation.pipeline-structure-test
  (:use [lambdacd.execution]
        [lambdacd.steps.control-flow]
        [lambdacd.test-util])
  (:require [clojure.test :refer :all]
            [lambdacd.presentation.pipeline-structure :refer :all]))

(defn do-stuff [] {})
(defn do-other-stuff [] {})
(defn do-more-stuff [] {})
(defn do-even-more-stuff [] {})


(def pipeline
  `(
    (in-parallel
      (in-cwd "some-path" ;; I can't checkout yet so this will to to set up a working dir
        do-stuff)
      (in-cwd "some-other-path"
        do-other-stuff
        do-more-stuff))
    (in-cwd "some-path"
      do-even-more-stuff)
  ))

(def simple-pipeline
  `(
    do-stuff
    do-other-stuff
   ))

(def foo-pipeline
  `((in-parallel
     (in-cwd "foo"
       do-stuff)
     (in-cwd "bar"
        do-other-stuff))))
(with-private-fns [lambdacd.presentation.pipeline-structure [display-type display-name]]
  (deftest display-type-test
    (testing "that in-parallel gets detected"
      (is (= :parallel (display-type `in-parallel)))
      (is (= :parallel (display-type (first (first pipeline)))))
    )
    (testing "that container types get detected"
      (is (= :container (display-type `in-cwd)))
      (is (= :container (display-type (first (second pipeline)))))
    )
    (testing "that normal steps get detected"
      (is (= :step (display-type `do-stuff)))
      (is (= :step (display-type (first simple-pipeline))))
    )
    (testing "that a string is unknown type"
      (is (= :unknown (display-type "foo")))
      (is (= :unknown (display-type (second (second (first pipeline))))))
    )
    (testing "that a sequence is a step" ; TODO: display-representation expects it this way. not entirely sure this is correct..
      (is (= :step (display-type `(do-stuff do-more-stuff))))
      (is (= :step (display-type simple-pipeline)))
    ))
  (deftest display-name-test
    (testing "that the display-name for a step is just the function-name"
      (is (= "do-even-more-stuff" (display-name `do-even-more-stuff)))
      (is (= "do-even-more-stuff" (display-name (last (second pipeline)))))
    )
    (testing "that the display-name for a parallel is just the function-name"
      (is (= "in-parallel" (display-name `in-parallel)))
      (is (= "in-parallel" (display-name (first (first pipeline)))))
    )))




(deftest display-representation-test
  (testing "that the display-representation of a step is the display-name and display-type"
    (is (= {:name "do-even-more-stuff" :type :step :step-id '()} (display-representation (last (second pipeline)))))
  )
  (testing "that the display-representation of a step with children has name, type and children"
    (is (= {:name "in-cwd"
            :type :container
            :step-id '()
            :children [{:name "do-even-more-stuff" :type :step :step-id '(1)}]} (display-representation (second pipeline))))
  )
  (testing "that a display-representation of a sequence of only steps works"
    (is (= [{:name "do-stuff" :type :step :step-id '(1)} {:name "do-other-stuff" :type :step :step-id '(2)}] (display-representation simple-pipeline))))
  (testing "that foo-pipeline works"
    (is (= [{:name "in-parallel"
             :type :parallel
             :step-id '(1)
             :children
               [{:name "in-cwd"
                 :type :container
                 :step-id '(1 1)
                 :children [{:name "do-stuff" :type :step :step-id '(1 1 1)}]}
               {:name "in-cwd"
                :type :container
                :step-id '(2 1)
                :children [{:name "do-other-stuff" :type :step :step-id '(1 2 1)}]}]}] (display-representation foo-pipeline))))
)
