(ns lambdacd.steps.support-test
  (:require [clojure.test :refer :all]
            [lambdacd.steps.support :as step-support :refer :all]
            [lambdacd.testsupport.test-util :refer [slurp-chan call-with-timeout]]
            [lambdacd.testsupport.data :refer [some-ctx some-ctx-with]]
            [lambdacd.testsupport.matchers :refer [map-containing]]
            [clojure.core.async :as async]
            [lambdacd.testsupport.noop-pipeline-state :as noop-pipeline-state]
            [lambdacd.execution.core :as execution]))

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
(defn some-step-receiving-only-args [args]
  {:status :success :the-arg (:v args)})

(defn some-step-receiving-nothing []
  {:status :success :no-arg :passed-in})

(defn some-step-returning-the-context-passed-in [args ctx]
  {:status :success :the-ctx-1 (:v ctx)})
(defn some-step-receiving-only-ctx [ctx]
  {:status :success :the-ctx-1 (:v ctx)})

(defn some-other-step-returning-the-context-passed-in [args ctx]
  {:status :success :the-ctx-2 (:v ctx)})

(defn some-step-saying-hello [args ctx]
  {:status :success :out "hello"})

(defn some-step-saying-world [args ctx]
  {:status :success :out "world"})

(defn some-step-printing-to-intermediate-output [args ctx]
  (capture-output ctx
    (println "helloworld")
    {:status :success}))

(defn some-step-with-additional-arguments [args ctx hello world]
  {:status :success :hello hello :world world})

(defn some-step-with-arbitrary-arguments [hello world]
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

(deftest chain-steps-test ; TODO: flaky
  (testing "chain-steps and always-chain-steps"
    (doall (for [unit-under-test [chain-steps always-chain-steps]]
             (do
               (testing "that the input argument is passed to the first step"
                 (is (map-containing {:status :success :the-arg 42}
                                     (unit-under-test {:v 42} (some-ctx)
                                                      some-step-returning-an-argument-passed-in))))
               (testing "that the input argument is passed to all the steps"
                 (is (map-containing {:status :success :the-arg 42}
                                     (unit-under-test {:v 42} (some-ctx)
                                                      some-successful-step some-step-returning-an-argument-passed-in))))
               (testing "that the results of two steps get merged"
                 (is (map-containing {:status :success :foo :baz}
                                     (unit-under-test {} (some-ctx)
                                                      some-step some-other-step))))
               (testing "that the results of the first step are the input for the next step"
                 (is (map-containing {:status :success :the-arg 42 :v 42}
                                     (unit-under-test {} (some-ctx)
                                                      some-step-that-returns-a-value
                                                      some-step-returning-an-argument-passed-in))))
               (testing "that global values are being kept over all steps"
                 (is (map-containing {:status         :success
                                      :the-global-arg 42
                                      :global         {:g 42 :v 21}}
                                     (unit-under-test {} (some-ctx) some-step-returning-a-global
                                                      some-step-returning-a-different-global
                                                      some-step-returning-a-global-argument-passed-in))))
               (testing "that overlapping string-outputs get concatenated"
                 (is (map-containing {:status :success
                                      :out    "hello\nworld"} (unit-under-test {} (some-ctx) some-step-saying-hello some-step-saying-world))))
               (testing "that intermediate outputs are kept while step is running (flaky)"
                 (let [result-channel (async/chan 100)
                       ctx (some-ctx-with :result-channel result-channel
                                          :pipeline-state-component (noop-pipeline-state/new-no-op-pipeline-state)
                                          :config {:config {:step-updates-per-sec nil}})]
                   (unit-under-test {} ctx some-step-saying-hello some-step-printing-to-intermediate-output)
                   (is (= [{:status :running}
                           {:status :success :out "hello"}
                           {:status :running :out "hello"}
                           {:status :running :out "hello\nhelloworld\n"}
                           {:status :success :out "hello\nhelloworld\n"}] (slurp-chan result-channel)))))
               (testing "that the context is passed to all the steps"
                 (let [result (unit-under-test {} (some-ctx-with :v 42)
                                               some-step-returning-the-context-passed-in
                                               some-other-step-returning-the-context-passed-in)]
                   (is (= 42 (:the-ctx-1 result)))
                   (is (= 42 (:the-ctx-2 result)))))
               (testing "that the steps individual outputs are available in the end result"
                 (is (map-containing {:outputs {[1 42] {:status :success}
                                                [2 42] {:status :success :v 42}}}
                                     (unit-under-test {} (some-ctx-with :step-id [42])
                                                      some-successful-step
                                                      some-step-that-returns-a-value))))))))
  (testing "only chain-steps"
    (testing "that a failing step stops the execution"
      (is (map-containing {:status :failure :foo :bar}
                          (chain-steps {} (some-ctx)
                                       some-step
                                       some-failling-step
                                       step-that-should-never-be-called))))
    (testing "that the steps individual outputs are available in the end result when aborted because of failing step"
      (is (map-containing {:outputs {[1 42] {:status :failure}}}
                          (chain-steps {} (some-ctx-with :step-id [42])
                                       some-failling-step
                                       some-successful-step)))))
  (testing "only always-chain-steps"
    (testing "that a failing step does not stop the execution but the step is still a failure in the end"
      (is (map-containing {:status :failure :foo :baz}
                          (always-chain-steps {} (some-ctx)
                                              some-step
                                              some-failling-step
                                              some-other-step))))))

(deftest chaining-test
  (testing "that we can just call a single step"
    (is (map-containing {:status :success :foo :bar} (chaining {} (some-ctx) (some-step injected-args injected-ctx)))))
  (testing "that the results of two steps get merged"
    (is (map-containing {:status :success :foo :baz}
                        (chaining {} (some-ctx)
                                  (some-step injected-args injected-ctx)
                                  (some-other-step injected-args injected-ctx)))))
  (testing "that a failing step stops the execution"
    (is (map-containing {:status :failure :foo :bar}
                        (chaining {} (some-ctx)
                                  (some-step injected-args injected-ctx)
                                  (some-failling-step injected-args injected-ctx)
                                  (step-that-should-never-be-called injected-args injected-ctx)))))
  (testing "that a given argument is passed on to the step"
    (is (map-containing {:status :success :the-arg 42}
                        (chaining {:v 42} (some-ctx)
                                  (some-step-returning-an-argument-passed-in injected-args injected-ctx))))
    (is (map-containing {:status :success :the-arg 42}
                        (chaining {:v 42} (some-ctx)
                                  (some-step-returning-an-argument-passed-in step-support/injected-args injected-ctx)))))
  (testing "that we can have more arguments than just args and ctx"
    (is (map-containing {:status :success :hello "hello" :world "world"}
                        (chaining {} (some-ctx) (some-step-with-additional-arguments injected-args injected-ctx "hello" "world")))))
  (testing "that we can also hardcode results at the end of the chain"
    (is (map-containing {:status :success :this-is :test :foo :bar}
                        (chaining {} (some-ctx)
                                  (some-step injected-args injected-ctx)
                                  {:status :success :this-is :test}))))
  (testing "that a given context is passed on to the step"
    (is (= 42 (:the-ctx-1 (chaining {} (some-ctx-with :v 42)
                                                      (some-step-returning-the-context-passed-in injected-args injected-ctx))))))
  (testing "that we can pass in only the ctx"
    (is (= 42 (:the-ctx-1 (chaining {} (some-ctx-with :v 42)
                                                      (some-step-receiving-only-ctx injected-ctx))))))
  (testing "that we can pass in only the args"
    (is (map-containing {:status :success :the-arg 42}
                        (chaining {:v 42} (some-ctx)
                                  (some-step-receiving-only-args injected-args)))))
  (testing "that we can pass in no params at all"
    (is (map-containing {:status :success :no-arg :passed-in}
                        (chaining {} (some-ctx)
                                  (some-step-receiving-nothing)))))
  (testing "that we can pass in values but nothing else"
    (is (map-containing {:status :success :hello "hello" :world "world"}
                        (chaining {} (some-ctx)
                                  (some-step-with-arbitrary-arguments "hello" "world"))))
    (is (map-containing {:status :success :hello "hello" :world "world"}
                        (let [hello "hello"]
                          (chaining {} (some-ctx)
                                    (some-step-with-arbitrary-arguments hello "world"))))))
  (testing "that intermediate steps that return nil are ok and dont interfere"
    (is (map-containing {:status :success :foo :baz}
                        (chaining {} (some-ctx)
                                  (some-step injected-args injected-ctx)
                                  (print "")
                                  (some-other-step injected-args injected-ctx)))))
  (testing "that we can combine chaining and capture-output to debug intermediate results"
    (is (map-containing {:status :success :foo :baz :out "args: {:status :success, :foo :bar, :global nil}"}
                        (capture-output (some-ctx)
                          (chaining {} (some-ctx)
                                    (some-step injected-args injected-ctx)
                                    (print "args:" injected-args)
                                    (some-other-step injected-args injected-ctx)))))
    (is (map-containing {:status :success :foo :baz :out "foo-value: :bar"}
                        (capture-output (some-ctx)
                          (chaining {} (some-ctx)
                                    (some-step injected-args injected-ctx)
                                    (print "foo-value:" (:foo injected-args))
                                    (some-other-step injected-args injected-ctx)))))))

(deftest always-chaining-test
  (testing "that a failing step doesnt stop the execution"
    (is (map-containing {:status :failure :foo :baz}
                        (always-chaining {} (some-ctx)
                          (some-step injected-args injected-ctx)
                          (some-failling-step injected-args injected-ctx)
                          (some-other-step injected-args injected-ctx))))))

(deftest last-step-status-wins-test
  (testing "that it changes the step result to one where the status of the last step result wins"
    (is (map-containing {:status :success}
                        (last-step-status-wins {:status :failure :outputs {`(2 42) {:status :success}
                                                                           `(1 42) {:status :failure}}}))))
  (testing "that it keeps all the results"
    (is (map-containing {:foo :bar}
                        (last-step-status-wins {:status :failure :outputs {`(2 42) {:status :success}
                                                                           `(1 42) {:status :failure}}
                                                :foo    :bar})))))

(defn output-load-test-ctx []
  (some-ctx-with :pipeline-state-component (noop-pipeline-state/new-no-op-pipeline-state)))

(defn log-lots-of-output [args ctx]
  (doall (for [i (range 800)]
           (if-not-killed ctx
                          (async/>!! (:result-channel ctx) [:xyz i]))))
  {:status :success})

(defn log-lots-of-output-in-chaining [args ctx]
  (chaining args ctx
                    (log-lots-of-output injected-args injected-ctx)))

(deftest output-stress-test ; reproduces #135
  (testing "that we don't fail if we come across lots of output for just in general"
    (is (= :success (:status (call-with-timeout 10000
                                      (execution/execute-step {} [(output-load-test-ctx) log-lots-of-output]))))))
  (testing "that we don't fail if we come across lots of output for chaining"
    (is (= :success (:status (call-with-timeout 10000
                                      (execution/execute-step {} [(output-load-test-ctx) log-lots-of-output-in-chaining])))))))

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
  (testing "that details are merged correctly"
    (is (= {:a [{:b 2} {:c 3}]} (merge-step-results [{:a [{:b 2}]} {:a [{:c 3}]}]))))
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


(deftest unify-only-status-test
  (testing "that the converted function returns a proper step-result"
    (is (= {:status :some-status} ((unify-only-status (constantly :some-status)) {}))))
  (testing "that it converts the given function into one that receives only statuses"
    (is (= [:foo :bar] (:status ((unify-only-status identity) {[0]{:status :foo} [1]{:status :bar}}))))))
