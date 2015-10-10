(ns lambdacd.steps.support-test
  (:require [clojure.test :refer :all]
            [lambdacd.steps.support :refer :all]
            [lambdacd.testsupport.test-util :refer [slurp-chan]]
            [lambdacd.testsupport.data :refer [some-ctx some-ctx-with]]
            [lambdacd.testsupport.matchers :refer [map-containing]]
            [clojure.core.async :as async]))

(defn some-step [args ctx]
  {:status :success :foo :bar})

(defn some-other-step [args ctx]
  {:status :success :foo :baz})

(defn some-failling-step [args ctx]
  {:status :failure})
(defn some-successful-step [args ctx]
  {:status :success})

(defn some-step-that-returns-a-value [args ctx]
  {:v 42 :status :success})

(defn some-step-returning-a-global [args ctx]
  {:global {:g 42} :status :success})
(defn some-step-returning-a-different-global [args ctx]
  {:global {:v 21} :status :success})
(defn some-step-returning-a-global-argument-passed-in [args ctx]
  {:the-global-arg (:g (:global args)) :status :success})

(defn some-step-returning-an-argument-passed-in [args ctx]
  {:status :success :the-arg (:v args)})

(defn some-step-returning-the-context-passed-in [args ctx]
  {:status :success :the-ctx-1 ctx})

(defn some-other-step-returning-the-context-passed-in [args ctx]
  {:status :success :the-ctx-2 ctx})

(defn some-step-saying-hello [args ctx]
  {:status :success :out "hello"})
(defn some-step-saying-world [args ctx]
  {:status :success :out "world"})

(defn some-step-with-additional-arguments [args ctx hello world]
  {:status :success :hello hello :world world})

(defn step-that-should-never-be-called [args ctx]
  (throw (IllegalStateException. "do not call me!")))


(deftest print-to-output-test
  (testing "that it writes to the result-channel with every call and accumulates writes"
    (let [result-channel (async/chan 100)
          printer (new-printer)
          ctx (some-ctx-with :result-channel result-channel)]
      (print-to-output ctx printer "Hello")
      (print-to-output ctx printer "World")
      (is (= [[:out "Hello\n"]
              [:out "Hello\nWorld\n"]] (slurp-chan result-channel))))))

(deftest printed-output-test
  (testing "that we can get the things we printed before"
    (let [printer (new-printer)
          ctx (some-ctx)]
      (print-to-output ctx printer "Hello")
      (print-to-output ctx printer "World")
      (is (= "Hello\nWorld\n" (printed-output printer))))))

(deftest chain-steps-test
  (testing "that the input argument is passed to the first step"
    (is (= {:status :success :the-arg 42} (chain-steps {:v 42} {}
                                                                 some-step-returning-an-argument-passed-in))))
  (testing "that the input argument is passed to all the steps"
    (is (= {:status :success :the-arg 42} (chain-steps {:v 42} {}
                                                       some-successful-step some-step-returning-an-argument-passed-in))))
  (testing "that the results of two steps get merged"
    (is (= {:status :success :foo :baz} (chain-steps {} {}
                                                               some-step some-other-step))))
  (testing "that a failing step stops the execution"
    (is (= {:status :failure :foo :bar} (chain-steps {} {}
                                                               some-step
                                                               some-failling-step
                                                               step-that-should-never-be-called))))
  (testing "that the results of the first step are the input for the next step"
    (is (= {:status :success :the-arg 42 :v 42} (chain-steps {} {}
                                                                        some-step-that-returns-a-value
                                                                        some-step-returning-an-argument-passed-in))))
  (testing "that global values are being kept over all steps"
    (is (= {:status :success
            :the-global-arg 42
            :global {:g 42 :v 21}} (chain-steps {} {} some-step-returning-a-global
                                                      some-step-returning-a-different-global
                                                      some-step-returning-a-global-argument-passed-in))))
  (testing "that overlapping string-outputs get concatenated"
    (is (= {:status :success
            :out "hello\nworld"} (chain-steps {} {} [some-step-saying-hello
                                                               some-step-saying-world]))))
  (testing "that the context is passed to all the steps"
    (is (= {:status :success :the-ctx-1 {:v 42} :the-ctx-2 {:v 42}} (chain-steps {} {:v 42}
                                                                   some-step-returning-the-context-passed-in
                                                                   some-other-step-returning-the-context-passed-in))))
  (testing "that we can also use a step-vector as input (DEPRECATED)"
    (is (= {:status :success :the-arg 42} (chain-steps {:v 42} {}
                                                       [some-successful-step
                                                        some-step-returning-an-argument-passed-in])))))

; Note: if cursive complains about incorrect arity, that's cursive not knowing what the chain-macro does.
; as long as the tests are green, you can ignore this...
(deftest chain-test
  (testing "that we can just call a single step"
    (is (= {:status :success :foo :bar} (chain {} {} (some-step)))))
  (testing "that the results of two steps get merged"
    (is (= {:status :success :foo :baz} (chain {} {}
                                         (some-step)
                                         (some-other-step)))))
  (testing "that a failing step stops the execution"
    (is (= {:status :failure :foo :bar} (chain {} {}
                                          (some-step )
                                          (some-failling-step)
                                          (step-that-should-never-be-called)))))
  (testing "that a given argument is passed on to the step"
    (is (= {:status :success :the-arg 42} (chain {:v 42} {}
                                               (some-step-returning-an-argument-passed-in)))))
  (testing "that we can have more arguments than just args and ctx"
    (is (= {:status :success :hello "hello" :world "world"}
           (chain {} {} (some-step-with-additional-arguments "hello" "world")))))
  (testing "that we can also hardcode results at the end of the chain"
    (is (= {:status :success :this-is :test :foo :bar}
           (chain {} {}
                  (some-step)
                  {:status :success :this-is :test}))))
  (testing "that a given context is passed on to the step"
    (is (= {:status :success :the-ctx-1 {:v 42}} (chain {} {:v 42}
                                                 (some-step-returning-the-context-passed-in))))))

(deftest if-not-killed-test
  (testing "that the body will only be executed if step is still alive"
    (let [killed-ctx (some-ctx-with :is-killed (atom true))
          alive-ctx (some-ctx-with :is-killed (atom false))]
      (is (= {:status :success} (if-not-killed alive-ctx  {:status :success})))
      (is (= {:status :success} (if-not-killed alive-ctx  (assoc {} :status :success))))
      (is (= {:status :killed}  (if-not-killed killed-ctx   {:status :success})))))
  (testing "that the status is updated when the step was killed"
    (let [output (async/chan 10)
          killed-ctx (some-ctx-with :is-killed (atom true) :result-channel output)]
      (if-not-killed killed-ctx  {:status :success})
      (is (= [:status :killed] (async/<!! output))))))

(deftest merge-globals-test
  (testing "that only global values are returned"
    (is (= {:foo :bar} (merge-globals [{:x :y :global {:foo :bar}}]))))
  (testing "that it can merge global values from the outputs of several steps"
    (is (map-containing {:foo :bar :bar :baz} (merge-globals [{:x :y :global {:foo :bar}} {:global { :bar :baz}}]))))
  (testing "that later global values overwrite earlier ones"
    (is (= {:foo :baz} (merge-globals [{:x :y :global {:foo :bar}} {:x :y :global {:foo :baz}}]))))
  (testing "that nothing bad happens if steps don't have global values"
    (is (= {} (merge-globals [{:x :y } {} {:z 1 :global {}} ])))
    (is (= {} (merge-globals [ ])))
    (is (= {} (merge-globals [{:x :y } {} {:z 1} ])))))

(deftest merge-step-results-test
  (testing "that step-results are merged"
    (is (= {:foo :bar :bar :baz} (merge-step-results [{:foo :bar} {:bar :baz}]))))
  (testing "that nothing bad happens from empty results"
    (is (= { :bar :baz} (merge-step-results [{} {:bar :baz}])))
    (is (= { } (merge-step-results [])))
    (is (= { } (merge-step-results [{} {}]))))
  (testing "that later step-results overwrite earlier ones"
    (is (= {:foo :baz} (merge-step-results [{:foo :bar} {:foo :baz}]))))
  (testing "that there is a special status handling"
    (is (= {:status :success} (merge-step-results [{:status :success} {:status :success}])))
    (is (= {:status :failure} (merge-step-results [{:status :success} {:status :failure}])))
    (is (= {:status :unknown} (merge-step-results [{:status :unknown} {:status :success}]))) ; non-success trumps order
    (is (= {:status :unknown} (merge-step-results [{:status :failure} {:status :unknown}]))) ; non-success overwrites in order
    (is (= {:status :failure} (merge-step-results [{:status :failure} {:status :success}]))))) ; non-success trumps order


(deftest capture-output-test
  (testing "that the original step result is kept"
    (is (map-containing {:foo :bar :status :success}
                        (capture-output (some-ctx)
                                        (some-step nil nil)))))
  (testing "we can deal with static output"
    (is (map-containing {:foo :bar :status :success}
                        (capture-output (some-ctx)
                                        {:foo :bar :status :success}))))
  (testing "that everything written to stdout is written to the result channel as output"
    (let [result-channel (async/chan 100)
          ctx (some-ctx-with :result-channel result-channel)]
      (capture-output ctx
                      (println "Hello")
                      (println "World"))
      (is (= [[:out "Hello\n"]
              [:out "Hello\nWorld\n"]] (slurp-chan result-channel)))))
  (testing "that it returns the accumulated output"
    (is (map-containing {:out "Hello\nWorld\n"}
                        (capture-output (some-ctx)
                                        (println "Hello")
                                        (println "World")
                                        {:status :success}))))
  (testing "that a steps :out is appended to the captured output" ; appending was a more or less random decision,
    (is (map-containing {:out "Hello\nWorld\n\nFrom Step"}
                        (capture-output (some-ctx)
                                        (println "Hello")
                                        (println "World")
                                        {:status :success
                                         :out "From Step"}))))
  (testing "that hijacking *out* doesn't interfere with other threads"
    (let [not-stopped (atom true)]
      (async/thread (while @not-stopped
                      (print " "))
                    (println))
      (is (= "Hello\n" (:out
                          (capture-output (some-ctx)
                                          (println "Hello")
                                          {:status :success}))))
      (reset! not-stopped false)))
  (testing "that it can deal with a body that returns nil"
    (is (= nil (capture-output (some-ctx)
                               nil))))
  (testing "that it can deal with a body that returns something that is not a map"
    (is (= nil (capture-output (some-ctx)
                               "this is not a map")))))