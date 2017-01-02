(ns lambdacd.execution.internal.kill-test
  (:require [clojure.test :refer :all]
            [lambdacd.execution.internal.kill :refer :all]
            [lambdacd.execution.internal.execute-step :refer [execute-step]]
            [lambdacd.testsupport.matchers :refer [map-containing]]
            [lambdacd.testsupport.data :refer [some-ctx-with some-ctx]]
            [lambdacd.testsupport.test-util :refer [start-waiting-for
                                                    wait-for
                                                    step-running?
                                                    get-or-timeout
                                                    call-with-timeout]]
            [lambdacd.steps.support :as step-support]
            [lambdacd.steps.control-flow :as control-flow]
            [lambdacd.util.internal.temp :as temp-util]))

(defn some-step-waiting-to-be-killed [_ ctx]
  (loop [counter 0]
    (step-support/if-not-killed ctx
                                (if (< counter 100)         ;; make sure the step always eventually finishes
                                  (do
                                    (Thread/sleep 100)
                                    (recur (inc counter)))
                                  {:status :waited-too-long}))))

(defn some-step-flipping-the-kill-switch [_ {is-killed :is-killed}]
  (reset! is-killed true))

(deftest kill-step-integration-test
  (testing "that a running step can be killed"
    (let [is-killed          (atom false)
          ctx                (some-ctx-with :step-id [3 2 1]
                                            :build-number 3
                                            :is-killed is-killed)
          future-step-result (start-waiting-for (execute-step {} [ctx some-step-waiting-to-be-killed]))]
      (wait-for (step-running? ctx))
      (kill-step ctx 3 [3 2 1])
      (is (map-containing {:status :killed} (get-or-timeout future-step-result)))))
  (testing "that killing a step sets a marker that can be used to determine if a kill was received even if the step didnt handle it"
    (testing "a simple step"
      (let [is-killed          (atom false)
            ctx                (some-ctx-with :step-id [3 2 1]
                                              :build-number 3
                                              :is-killed is-killed)
            future-step-result (start-waiting-for (execute-step {} [ctx some-step-waiting-to-be-killed]))]
        (wait-for (step-running? ctx))
        (kill-step ctx 3 [3 2 1])

        (is (map-containing {:received-kill true} (first (vals (:outputs (get-or-timeout future-step-result))))))))
    (testing "that it works for child steps that are killed along with the parent"
      (let [is-killed          (atom false)
            parent-step-id     [3 2 1]
            child-step-id      [1 3 2 1]
            ctx                (some-ctx-with :step-id parent-step-id
                                              :build-number 3
                                              :is-killed is-killed)
            child-ctx          (assoc ctx :step-id child-step-id)
            future-step-result (start-waiting-for (execute-step {} [ctx (control-flow/run some-step-waiting-to-be-killed)]))]
        (wait-for (step-running? ctx))
        (wait-for (step-running? child-ctx))
        (kill-step ctx 3 [3 2 1])
        (is (map-containing {:received-kill true} (first (vals (:outputs (first (vals (:outputs (get-or-timeout future-step-result)))))))))))
    (testing "that a step using the kill-switch does not bubble up to the parents passing in the kill-switch"
      (let [is-killed (atom false)
            ctx       (some-ctx-with :is-killed is-killed)]
        (execute-step {} [ctx some-step-flipping-the-kill-switch])
        (is (= false @is-killed))))))

(deftest kill-all-pipelines-test
  (testing "that it kills root build steps in any pipeline"
    (let [ctx                (some-ctx-with :step-id [3])
          future-step-result (start-waiting-for (execute-step {} [ctx some-step-waiting-to-be-killed]))]
      (wait-for (step-running? ctx))
      (kill-all-pipelines ctx)
      (is (map-containing {:status :killed} (get-or-timeout future-step-result :timeout 1000)))))
  (testing "that it doesn't kill nested steps as they are killed by their parents"
    (let [ctx                (some-ctx-with :step-id [3 1])
          future-step-result (start-waiting-for (execute-step {} [ctx some-step-waiting-to-be-killed]))]
      (wait-for (step-running? ctx))
      (kill-all-pipelines ctx)
      (is (map-containing {:status :timeout} (get-or-timeout future-step-result :timeout 1000)))))
  (testing "that killing is idempotent"
    (let [ctx                (some-ctx-with :step-id [3])
          future-step-result (start-waiting-for (execute-step {} [ctx some-step-waiting-to-be-killed]))]
      (wait-for (step-running? ctx))
      (kill-all-pipelines ctx)
      (kill-all-pipelines ctx)
      (is (map-containing {:status :killed} (get-or-timeout future-step-result :timeout 1000)))
      (kill-all-pipelines ctx)))
  (testing "that it waits until all running pipelines are done"
    (let [started-steps (atom #{:foo})
          ctx           (some-ctx-with :step-id [3 1]
                                       :started-steps started-steps)]
      ; still running, should time out
      (is (map-containing {:status :timeout} (call-with-timeout 300 (kill-all-pipelines ctx))))
      (reset! started-steps #{})
      (is (not= {:status :timeout} (call-with-timeout 500 (kill-all-pipelines ctx))))))
  (testing "that waiting for pipeline completion times out"
    (let [started-steps (atom #{:foo})
          ctx           (some-ctx-with :step-id [3 1]
                                       :started-steps started-steps
                                       :config {:ms-to-wait-for-shutdown 200
                                                :home-dir                (temp-util/create-temp-dir)})]
      (is (not= {:status :timeout} (call-with-timeout 1000 (kill-all-pipelines ctx)))))))
