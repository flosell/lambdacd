(ns lambdacd.event-bus-test
  (:require [clojure.test :refer :all]
            [lambdacd.event-bus :refer :all]
            [lambdacd.testsupport.test-util :refer :all]
            [lambdacd.util :refer [buffered]]
            [lambdacd.testsupport.data :refer :all]
            [clojure.core.async :as async]
            [lambdacd.testsupport.noop-pipeline-state :as noop-pipeline-state]
            [lambdacd.util :as util]))

(defn- make-sure-subscriber-has-started []
  ; TODO: check if this is still necessary once we switch to the new event bus
  (Thread/sleep 100))

(defn- append-to! [a msg]
  (swap! a #(conj % msg)))

(defn- publish-200-messages [ctx published-messages]
  (async/go-loop [i 0]
    (if (< i 200)
      (do
        (publish! ctx :foo i)
        (append-to! published-messages i)
        (recur (inc i))))))

(defn- subscribe-read-100-messages-unsubscribe [ctx received-messages]
  (async/go
    (let [subscription (subscribe ctx :foo)
          payload      (only-payload subscription)]
      (loop [i 0]
        (if (< i 100)
          (do
            (append-to! received-messages (async/<! payload))
            (recur (inc i)))))
      (unsubscribe ctx :foo subscription))))

(defn- subscriber-with-feedback [ctx received-messages]
  (async/go
    (let [subscription (subscribe ctx :foo)
          payload      (only-payload subscription)]
      (loop []
        (if-let [msg (async/<! payload)]
          (do
            (append-to! received-messages msg)
            (publish! ctx :bar msg)
            (recur))))
      (unsubscribe ctx :foo subscription))))

(defn- some-ctx-with-event-bus-switched [new-event-bus?]
  (some-ctx-with :config {:use-new-event-bus new-event-bus?}
                 :pipeline-state-component (noop-pipeline-state/new-no-op-pipeline-state)))

(deftest event-bus-test
  (doall (for [new-event-bus? [false true]]
           (testing (str "new event-bus" new-event-bus?)
             (testing "that we can publish, subscribe and unsubscribe"
               (let [ctx                (initialize-event-bus (some-ctx-with-event-bus-switched new-event-bus?))
                     published-messages (atom [])
                     received-messages  (atom [])]
                 (subscribe-read-100-messages-unsubscribe ctx received-messages)
                 (make-sure-subscriber-has-started)
                 (publish-200-messages ctx published-messages)

                 (is-eventually (= 200 (count @published-messages)))
                 (is-eventually (= 100 (count @received-messages)))))
             (testing "that we can subscribe multiple times and get the same messages"
               (let [ctx                (initialize-event-bus (some-ctx-with-event-bus-switched new-event-bus?))
                     published-messages (atom [])
                     received-messages-1  (atom [])
                     received-messages-2  (atom [])]
                 (subscribe-read-100-messages-unsubscribe ctx received-messages-1)
                 (subscribe-read-100-messages-unsubscribe ctx received-messages-2)
                 (make-sure-subscriber-has-started)
                 (publish-200-messages ctx published-messages)

                 (is-eventually (= 200 (count @published-messages)))
                 (is-eventually (= 100 (count @received-messages-1)))
                 (is-eventually (= 100 (count @received-messages-2)))

                 (is (= (take 100 @published-messages) @received-messages-1))
                 (is (= (take 100 @published-messages) @received-messages-2))))
             (testing "that we can publish even though nobody's listening"
               (let [ctx                (initialize-event-bus (some-ctx-with-event-bus-switched new-event-bus?))
                     published-messages (atom [])]
                 (publish-200-messages ctx published-messages)

                 (is-eventually (= 200 (count @published-messages)))))))))

(deftest deadlock-test
  (testing "that we don't deadlock when a subscriber triggers new events"
    (let [ctx                (initialize-event-bus (some-ctx-with-event-bus-switched true))
          published-messages (atom [])
          received-messages  (atom [])]
      (subscriber-with-feedback ctx received-messages)
      (make-sure-subscriber-has-started)
      (publish-200-messages ctx published-messages)

      (is-eventually (= 200 (count @published-messages)))
      (is-eventually (= 200 (count @received-messages))))))
