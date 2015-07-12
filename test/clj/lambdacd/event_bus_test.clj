(ns lambdacd.event-bus-test
  (:require [clojure.test :refer :all]
            [lambdacd.event-bus :refer :all]
            [lambdacd.testsupport.test-util :refer :all]
            [lambdacd.testsupport.data :refer :all]
            [clojure.core.async :as async]))


(deftest event-buffer-test
  (testing "that we can publish and subscribe to events"
    (let [ctx (initialize-event-bus (some-ctx))]
      (publish ctx :test-messages {:message-number 1})
      (let [subscription (subscribe ctx :test-messages)]
        (publish ctx :test-messages {:message-number 2})
        (publish ctx :other-topic {:other-message "hello"})
        (unsubscribe ctx :test-messages subscription)
        (publish ctx :test-messages {:message-number 3})
        (is (= [{:message-number 2}] (slurp-chan (only-payload subscription)))))))
  (testing "that messages get delivered to all subscribers if more than one subscribes to the same topic"
    (let [ctx (initialize-event-bus (some-ctx))
          subscription-1 (subscribe ctx :test-messages)
          subscription-2 (subscribe ctx :test-messages)
          payloads-1 (buffered (only-payload subscription-1))
          payloads-2 (buffered (only-payload subscription-2))]
      (publish ctx :test-messages {:message-number 1})
      (publish ctx :test-messages {:message-number 2})

      (is (= [{:message-number 1} {:message-number 2}] (slurp-chan payloads-1)))
      (is (= [{:message-number 1} {:message-number 2}] (slurp-chan payloads-2))))))
