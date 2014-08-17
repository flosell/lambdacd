(ns lambdaci.dsl-test
  (:require [clojure.test :refer :all]
            [lambdaci.dsl :refer :all]))

(defn some-step-processing-input [arg & _]
  (assoc arg :foo :baz))

(defn some-step [arg & _]
  {:foo :baz})

(defn some-step-for-cwd [{cwd :cwd} & _]
  {:foo cwd})



(deftest step-result-merge-test
  (testing "merging without collisions"
    (is (= {:foo "hello" :bar "world"} (merge-step-results {:foo "hello"} {:bar "world"}))))
;  (testing "merging with collisions"
;    (is (= {:foo ["hello" "world"]} (merge-step-results {:foo "hello"} {:foo "world"}))))
  (testing "merging of nested maps"
    (is (= {:outputs {[1 0] {:foo :baz} [2 0] {:foo :baz}}}
           (merge-step-results {:outputs {[1 0] {:foo :baz}}} {:outputs { [2 0] {:foo :baz}}}))))
  (testing "merging into a flat list on collision"
    (is (= {:foo ["hello" "world" "test"]} (merge-step-results {:foo ["hello" "world"]} {:foo "test"}))))
)

(deftest execute-step-test
  (testing "that executing returns the step result added to the input args"
    (is (= {:outputs { [0 0] {:foo :baz :x :y}}} (execute-step-new some-step-processing-input {:x :y} [0 0])))))

(deftest step-id-test
  (testing "that we can generate proper step-ids for steps"
    (is (= [[[1 0] some-step] [[2 0] some-step]] (steps-with-ids [some-step some-step] [0 0])))))

(deftest execute-steps-test
  (testing "that executing steps returns outputs of both steps with different step ids"
    (is (= {:outputs { [1 0] {:foo :baz} [2 0] {:foo :baz}}} (execute-steps-new [some-step some-step] {} [0 0])))))


(deftest in-cwd-test
  (testing "that it collects all the outputs together correctly and passes cwd to steps"
    ;; FIXME: the :cwd shouldn't be in output?!!?
    (is (= {:outputs { [1 0 0] {:foo "somecwd"} [2 0 0] {:foo :baz}} :cwd "somecwd"} ((in-cwd "somecwd" some-step-for-cwd some-step) {} [0 0])))))

(deftest in-parallel-test
  (testing "that it collects all the outputs together correctly"
    (is (= {:outputs { [1 0 0] {:foo :baz} [2 0 0] {:foo :baz}}} ((in-parallel some-step some-step) {} [0 0])))))
