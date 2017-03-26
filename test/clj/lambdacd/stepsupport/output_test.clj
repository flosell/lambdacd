(ns lambdacd.stepsupport.output-test
  (:require [clojure.test :refer :all]
            [lambdacd.stepsupport.output :refer :all]
            [lambdacd.testsupport.test-util :refer [slurp-chan call-with-timeout]]
            [lambdacd.testsupport.data :refer [some-ctx some-ctx-with]]
            [lambdacd.testsupport.matchers :refer [map-containing]]
            [clojure.core.async :as async]
            [lambdacd.testsupport.noop-pipeline-state :as noop-pipeline-state]
            [lambdacd.execution.core :as execution]
            [lambdacd.stepsupport.killable :as killable]
            [lambdacd.stepsupport.chaining :as chaining]))

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

(defn output-load-test-ctx []
  (some-ctx-with :pipeline-state-component (noop-pipeline-state/new-no-op-pipeline-state)))

(defn log-lots-of-output [args ctx]
  (doall (for [i (range 800)]
           (killable/if-not-killed ctx
                          (async/>!! (:result-channel ctx) [:xyz i]))))
  {:status :success})

(defn log-lots-of-output-in-chaining [args ctx]
  (chaining/chaining args ctx
                     (log-lots-of-output chaining/injected-args chaining/injected-ctx)))

(deftest output-stress-test ; reproduces #135
  (testing "that we don't fail if we come across lots of output for just in general"
    (is (= :success (:status (call-with-timeout 10000
                                      (execution/execute-step {} [(output-load-test-ctx) log-lots-of-output]))))))
  (testing "that we don't fail if we come across lots of output for chaining"
    (is (= :success (:status (call-with-timeout 10000
                                      (execution/execute-step {} [(output-load-test-ctx) log-lots-of-output-in-chaining])))))))

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

