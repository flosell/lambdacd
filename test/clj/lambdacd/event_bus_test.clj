(ns lambdacd.event-bus-test
  (:require [clojure.test :refer :all]
            [lambdacd.event-bus :refer :all]
            [lambdacd.testsupport.test-util :refer :all]
            [lambdacd.util :refer [buffered]]
            [lambdacd.testsupport.data :refer :all]
            [clojure.core.async :as async]))

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

(deftest event-bus-test
  (testing "that we can publish, subscribe and unsubscribe"
    (let [ctx                (initialize-event-bus (some-ctx))
          published-messages (atom [])
          received-messages  (atom [])]
      (subscribe-read-100-messages-unsubscribe ctx received-messages)
      (publish-200-messages ctx published-messages)

      (is-eventually (= 200 (count @published-messages)))
      (is-eventually (= 100 (count @received-messages)))))
  (testing "that we can subscribe multiple times and get the same messages"
    (let [ctx                (initialize-event-bus (some-ctx))
          published-messages (atom [])
          received-messages-1  (atom [])
          received-messages-2  (atom [])]
      (subscribe-read-100-messages-unsubscribe ctx received-messages-1)
      (subscribe-read-100-messages-unsubscribe ctx received-messages-2)
      (publish-200-messages ctx published-messages)

      (is-eventually (= 200 (count @published-messages)))
      (is-eventually (= 100 (count @received-messages-1)))
      (is-eventually (= 100 (count @received-messages-2)))

      (is (= (take 100 @published-messages) @received-messages-1))
      (is (= (take 100 @published-messages) @received-messages-2)))))
