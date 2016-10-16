(ns lambdacd.presentation.pipeline-structure-test
  (:use
        [lambdacd.steps.control-flow]
        [lambdacd.testsupport.test-util])
  (:require [clojure.test :refer :all]
            [lambdacd.presentation.pipeline-structure :refer :all]
            [lambdacd.steps.control-flow :as control-flow])
  (:refer-clojure :exclude [alias]))

(defn do-stuff [& ] {})
(defn ^{:depends-on-previous-steps true} do-other-stuff [] {})
(defn ^{:depends-on-previous-steps false} step-that-declares-it-doesnt-depend [] {})
(defn do-more-stuff [] {})
(defn do-even-more-stuff [] {})
(defn ^{:display-type :some-display-type} do-stuff-that-has-a-different-display-type [] {})
(defn do-stuff-with-hidden-params [^:hide a b])

(defn container-without-display-type [& steps]
  (fn [args ctx]))

(def pipeline
  `(
    (in-parallel
      (in-cwd "some-path" ;; I can't checkout yet so this will to to set up a working dir
        do-stuff)
      (in-cwd "some-other-path"
        do-other-stuff
        do-more-stuff))
    (in-cwd "some-path"
      do-even-more-stuff)))


(def simple-pipeline
  `(
    do-stuff
    do-other-stuff
    do-stuff-that-has-a-different-display-type))

(def pipeline-with-alias
  `(
     (alias "do-stuff-alias" do-stuff)
    do-other-stuff))

(def pipeline-with-nils
  `(
     do-stuff
     nil
     ~(if false
        do-other-stuff)
     do-other-stuff))

(def pipeline-with-nil-children
  `((in-cwd "bar"
            nil
            do-stuff)))

(def foo-pipeline
  `((in-parallel
     (in-cwd "foo"
       do-stuff)
     (in-cwd "bar"
        do-other-stuff))))

(def foo-pipeline-display-representation
  [{:name "in-parallel"
    :type :parallel
    :step-id '(1)
    :has-dependencies false
    :children
    [{:name "in-cwd foo"
      :type :container
      :step-id '(1 1)
      :has-dependencies false
      :children [{:name "do-stuff" :type :step :step-id '(1 1 1) :has-dependencies false}]}
     {:name "in-cwd bar"
      :type :container
      :step-id '(2 1)
      :has-dependencies false
      :children [{:name "do-other-stuff" :type :step :step-id '(1 2 1) :has-dependencies true}]}]}])

(def pipeline-with-container-without-display-type
  `((container-without-display-type
      do-stuff)))

(defn mk-pipeline [some-param]
  `((in-cwd "bar"
            (do-stuff ~some-param))))
(with-private-fns [lambdacd.presentation.pipeline-structure [display-type display-name has-dependencies?]]
  (deftest display-type-test
    (testing "that in-parallel gets detected"
      (is (= :parallel (display-type `(in-parallel do-stuff))))
      (is (= :parallel (display-type (first pipeline)))))
    (testing "that container types get detected"
      (is (= :container (display-type `(in-cwd "foo" do-stuff))))
      (is (= :container (display-type (second pipeline)))))
    (testing "that normal steps get detected"
      (is (= :step (display-type `do-stuff)))
      (is (= :step (display-type (first simple-pipeline)))))
    (testing "that display-types on normal steps work"
      (is (= :some-display-type (display-type `do-stuff-that-has-a-different-display-type)))
      (is (= :some-display-type (display-type (third simple-pipeline)))))
    (testing "that a string is unknown type"
      (is (= :unknown (display-type "foo")))
      (is (= :unknown (display-type (second (second (first pipeline)))))))
    (testing "that nil is an unknown type"
      (is (= :unknown (display-type nil))))
    (testing "that bool is an unknown type"
      (is (= :unknown (display-type true)))
      (is (= :unknown (display-type false))))
    (testing "that a sequence with child-steps is a container"
      (is (= :container (display-type `(do-stuff do-more-stuff))))
      (is (= :container (display-type simple-pipeline))))
    (testing "that a sequence with only parameters is a step"
      (is (= :step (display-type `(do-stuff "hello world")))))
    (testing "that a sequence with no parameters is a step"
      (is (= :step (display-type `(do-stuff)))))
    (testing "that deeper nesting with mixed display types works (reproduces #47)"
      (is (= :container (display-type `(run (in-parallel do-stuff)))))))
  (deftest display-name-test
    (testing "that the display-name for a step is just the function-name"
      (is (= "do-even-more-stuff" (display-name `do-even-more-stuff)))
      (is (= "do-even-more-stuff" (display-name (last (second pipeline))))))
    (testing "that the display-name for a parallel is just the function-name"
      (is (= "in-parallel" (display-name `in-parallel)))
      (is (= "in-parallel" (display-name (first (first pipeline))))))
    (testing "that the display-name for parameterized step is the function name and the parameter"
      (testing "no parameters"
        (is (= "do-stuff" (display-name `(do-stuff)))))
      (testing "values as parameters"
        (is (= "do-stuff hello world" (display-name `(do-stuff "hello world"))))
        (is (= "do-stuff :foo" (display-name `(do-stuff :foo))))
        (is (= "do-stuff 42 1.3" (display-name `(do-stuff 42 1.3)))))
      (testing "actual parameters"
        (let [some-value :foo]
          (is (= "do-stuff :foo" (display-name `(do-stuff ~some-value))))))
      (testing "steps with parameters and child-steps"
        (is (= "in-cwd some-cwd" (display-name `(in-cwd "some-cwd" do-stuff do-more-stuff))))
        (is (= "in-cwd some-cwd" (display-name `(in-cwd "some-cwd" do-stuff (do-stuff "foo"))))))
      (testing "hiding parameters"
        (is (= "do-stuff-with-hidden-params visible" (display-name `(do-stuff-with-hidden-params "hidden" "visible")))))
      (testing "that the display-name for alias is just the alias parameter without the function name"
        (is (= "the alias" (display-name `(control-flow/alias "the alias" do-stuff)))))
      (testing "nil children"
        (is (= "do-stuff" (display-name `(do-stuff nil))))
        (is (= "do-stuff foo" (display-name `(do-stuff nil "foo" nil)))))))
  (deftest dependencies-of-test
    (testing "that normal steps don't depend on anything"
      (is (= false (has-dependencies? `do-stuff)))
      (is (= false (has-dependencies? (first simple-pipeline)))))
    (testing "that it is true when step declares it depends on something"
      (is (= true (has-dependencies? `do-other-stuff)))
      (is (= true (has-dependencies? (second simple-pipeline)))))
    (testing "that it is false if a step declares it doesn't depend on anything"
      (is (= false (has-dependencies? `step-that-declares-it-doesnt-depend))))
    (testing "that it works with nested steps"
      (is (= false (has-dependencies? `(do-stuff "foo"))))
      (is (= true (has-dependencies? `(do-other-stuff "foo")))))))

(deftest display-representation-test
  (testing "that the display-representation of a step is the display-name and display-type"
    (is (= {:name "do-even-more-stuff" :type :step :step-id '() :has-dependencies false} (step-display-representation (last (second pipeline)) '()))))
  (testing "that the display-representation of a step with children has name, type and children"
    (is (= {:name "in-cwd some-path"
            :type :container
            :step-id '()
            :has-dependencies false
            :children [{:name "do-even-more-stuff" :type :step :step-id '(1) :has-dependencies false}]} (step-display-representation (second pipeline) '()))))
  (testing "that a display-representation of a sequence of only steps works"
    (is (= [{:name "do-stuff" :type :step :step-id '(1) :has-dependencies false}
            {:name "do-other-stuff" :type :step :step-id '(2) :has-dependencies true}
            {:name "do-stuff-that-has-a-different-display-type" :type :some-display-type :step-id '(3) :has-dependencies false}]
           (pipeline-display-representation simple-pipeline))))
  (testing "that aliasing makes the aliased child disappear"
    (is (= [{:name "do-stuff-alias" :type :step :step-id '(1 1) :has-dependencies false} {:name "do-other-stuff" :type :step :step-id '(2) :has-dependencies true}] (pipeline-display-representation pipeline-with-alias))))
  (testing "that foo-pipeline works"
    (is (= foo-pipeline-display-representation (pipeline-display-representation foo-pipeline))))
  (testing "that nesting parallel in sequential containers works (reproduces #47)"
    (is (not (nil? (:children (first (pipeline-display-representation `((run (in-parallel do-stuff))))))))))
  (testing "that display type defaults to :container for container-steps"
    (is (= [{:name "container-without-display-type"
             :step-id '(1)
             :type :container
             :has-dependencies false
             :children [{:name "do-stuff" :type :step :step-id '(1 1) :has-dependencies false}]}]
           (pipeline-display-representation pipeline-with-container-without-display-type))))
  (testing "that we support parameterized functions returning pipelines"
    (is (= [{:name "in-cwd bar"
             :type :container
             :step-id '(1)
             :has-dependencies false
             :children [{:name "do-stuff foo" :type :step :step-id '(1 1) :has-dependencies false}]}] (pipeline-display-representation (mk-pipeline "foo")))))
  (testing "that nil-values in a pipeline are ignored in a sequence of steps"
    (is (= [{:name "do-stuff" :type :step :step-id '(1) :has-dependencies false}
            {:name "do-other-stuff" :type :step :step-id '(2) :has-dependencies true}]
           (pipeline-display-representation pipeline-with-nils))))
  (testing "that nil-values in a pipeline are ignored as children"
    (is (= [{:name "in-cwd bar"
             :type :container
             :step-id '(1)
             :has-dependencies false
             :children [{:name "do-stuff" :type :step :step-id '(1 1) :has-dependencies false}]}]
           (pipeline-display-representation pipeline-with-nil-children)))))
