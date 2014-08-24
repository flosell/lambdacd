(ns lambdaci.dsl-test
  (:require [clojure.test :refer :all]
            [lambdaci.dsl :refer :all]))



(defmacro my-time
  "measure the time a function took to execute"
  [expr]
  `(let [start# (. System (nanoTime))]
     ~expr
     (/ (double (- (. System (nanoTime)) start#)) 1000000.0)))


(defn absolute-difference ^double [^double x ^double y]
  (Math/abs (double (- x y))))

(defn close? [tolerance x y]
  (< (absolute-difference x y) tolerance))


(defn some-step-processing-input [arg & _]
  (assoc arg :foo :baz))

(defn some-step [arg & _]
  {:foo :baz})

(defn some-step-for-cwd [{cwd :cwd} & _]
  {:foo cwd})

(defn some-step-taking-10ms [arg & _]
  (Thread/sleep 10)
  {:foo :bar})


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
    (is (= {:outputs { [0 0] {:foo :baz :x :y}}} (execute-step some-step-processing-input {:x :y} [0 0])))))

(deftest step-id-test
  (testing "that we can generate proper step-ids for steps"
    (is (= [[[1 0] some-step] [[2 0] some-step]] (steps-with-ids [some-step some-step] [0 0])))))

(deftest execute-steps-test
  (testing "that executing steps returns outputs of both steps with different step ids"
    (is (= {:outputs { [1 0] {:foo :baz} [2 0] {:foo :baz}}} (execute-steps [some-step some-step] {} [0 0])))))


(deftest in-cwd-test
  (testing "that it collects all the outputs together correctly and passes cwd to steps"
    ;; FIXME: the :cwd shouldn't be in output?!!?
    (is (= {:outputs { [1 0 0] {:foo "somecwd"} [2 0 0] {:foo :baz}} :cwd "somecwd"} ((in-cwd "somecwd" some-step-for-cwd some-step) {} [0 0])))))

(deftest timing-test
  (testing "that my-time more or less accurately measures the execution time of a step"
    (is (close? 2 10 (my-time (some-step-taking-10ms {}))))))

(deftest in-parallel-test
  (testing "that it collects all the outputs together correctly"
    (is (= {:outputs { [1 0 0] {:foo :baz} [2 0 0] {:foo :baz}}} ((in-parallel some-step some-step) {} [0 0]))))
  (testing "that it executes things faster than it would serially"
    (is (close? 3 10 (my-time ((in-parallel some-step-taking-10ms in-parallel some-step-taking-10ms in-parallel some-step-taking-10ms) {} [0 0]))))))
