(ns lambdacd.execution.internal.execute-steps-test
  (:require [clojure.test :refer :all]
            [lambdacd.execution.internal.execute-steps :refer :all]
            [lambdacd.testsupport.test-util :refer :all]
            [lambdacd.util :refer [buffered]]
            [lambdacd.testsupport.matchers :refer :all]
            [lambdacd.testsupport.data :refer [some-ctx-with some-ctx]]))

(defn some-step [arg & _]
  {:foo :baz})


(deftest context-for-steps-test
         (testing "that we can generate proper contexts for steps and keep other context info as it is"
                  (let [result (contexts-for-steps [some-step some-step] (some-ctx-with :some-value 42 :step-id [0]))]
                    (is (= some-step (second (first result))))
                    (is (= some-step (second (second result))))
                    (is (= 42 (:some-value (first (first result)))))
                    (is (= 42 (:some-value (first (second result)))))
                    (is (= [1 0] (:step-id (first (first result)))))
                    (is (= [2 0] (:step-id (first (second result))))))))
(defn some-other-step [arg & _]
  {:foo :baz :status :success})
(defn some-successful-step [arg & _]
  {:status :success})
(defn some-failing-step [arg & _]
  {:status :failure})

(defn some-step-returning-foobar-value [& _]
  {:foobar 42 :status :success})
(defn some-step-returning-ten-as-foobar-value [& _]
  {:foobar 10 :status :success})

(defn some-step-returning-global-foobar-value [& _]
  {:global {:foobar 42} :status :success})
(defn some-step-returning-another-global-foobar-value [& _]
  {:global {:foobar 43} :status :success})

(defn some-step-using-foobar-value [{foobar :foobar} & _]
  {:foobar-times-ten (* 10 foobar) :status :success})

(defn some-step-using-global-foobar-value [{{foobar :foobar} :global} & _]
  {:foobar-times-ten (* 10 foobar) :status :success})

(defn step-that-expects-a-kill-switch [_ {is-killed :is-killed}]
  {:status (if (nil? is-killed) :is-killed-not-there :success)})

(deftest execute-steps-integration-test
  (testing "that executing steps returns outputs of both steps with different step ids"
    (is (= {:outputs { [1 0] {:foo :baz :status :success} [2 0] {:foo :baz :status :success}} :status :success}
           (execute-steps [some-other-step some-other-step] {} (some-ctx-with :step-id [0])))))
  (testing "that a failing step prevents the succeeding steps from being executed"
    (is (= {:outputs { [1 0] {:status :success} [2 0] {:status :failure}} :status :failure}
           (execute-steps [some-successful-step some-failing-step some-other-step] {} (some-ctx-with :step-id [0])))))
  (testing "that the results of one step are the inputs to the other step"
    (is (= {:outputs { [1 0] {:status :success :foobar 42} [2 0] {:status :success :foobar-times-ten 420}} :status :success}
           (execute-steps [some-step-returning-foobar-value some-step-using-foobar-value] {} (some-ctx-with :step-id [0])))))
  (testing "that the original input is passed into all steps"
    (testing "the normal case"
      (is (= {:outputs { [1 0] {:status :success :foobar-times-ten 420} [2 0] {:status :success :foobar-times-ten 420}} :status :success}
             (execute-steps [some-step-using-foobar-value some-step-using-foobar-value] {:foobar 42} (some-ctx-with :step-id [0])))))
    (testing "step result overlays the original input"
      (is (= {:outputs { [1 0] {:status :success :foobar 10} [2 0] {:status :success :foobar-times-ten 100}} :status :success}
             (execute-steps [some-step-returning-ten-as-foobar-value some-step-using-foobar-value] {:foobar 42} (some-ctx-with :step-id [0]))))))
  (testing "that a steps :global results will be passed on to all subsequent steps"
    (is (= {:outputs {[1 0] {:status :success :global { :foobar 42}}
                      [2 0] {:status :success :foobar-times-ten 420}
                      [3 0] {:status :success :foobar-times-ten 420}}
            :status :success}
           (execute-steps [some-step-returning-global-foobar-value some-step-using-global-foobar-value some-step-using-global-foobar-value] {} (some-ctx-with :step-id [0])))))
  (testing "that a step overrides a :global value"
    (is (= {:outputs {[1 0] {:status :success :global {:foobar 42}}
                      [2 0] {:status :success :global {:foobar 43}}
                      [3 0] {:status :success :foobar-times-ten 430}}
            :status  :success}
           (execute-steps [some-step-returning-global-foobar-value some-step-returning-another-global-foobar-value some-step-using-global-foobar-value] {} (some-ctx-with :step-id [0])))))
  (testing "that execute steps injects a kill-switch by default" ; TODO: is this a feature of execute-steps? isn't this covered by execute-step?
    (is (= {:outputs { [1 0] {:status :success} [2 0] {:status :success}} :status :success}
           (execute-steps [some-successful-step step-that-expects-a-kill-switch] {} (some-ctx-with :step-id [0])))))
  (testing "that nil values (e.g. from an optional step in the structure) do not cause problems and are ignored"
    (is (= {:outputs { [1 0] {:status :success}} :status :success}
           (execute-steps [some-successful-step nil] {} (some-ctx-with :step-id [0]))))))



