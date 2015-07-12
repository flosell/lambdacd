(ns lambdacd.steps.manualtrigger-test
  (:require [clojure.test :refer :all]
            [clojure.core.async :as async]
            [lambdacd.steps.manualtrigger :refer :all]
            [lambdacd.testsupport.test-util :refer :all]
            [lambdacd.testsupport.data :refer :all]))

(deftest manualtrigger-test
  (testing "that the trigger is released after it was notified by something"
    (let [result-channel (async/chan 100)
          ctx (some-ctx-with :result-channel result-channel)
          trigger-id-ch (start-waiting-for-result :trigger-id result-channel)
          waiting-ch (start-waiting-for (wait-for-manual-trigger {} ctx))
          trigger-id (async/<!! trigger-id-ch) ]
      (post-id ctx trigger-id {})
      (is (= {:status :success} (get-or-timeout waiting-ch)))))
  (testing "that the trigger is not released if no notification happens"
    (let [result-channel (async/chan 100)
          ctx (some-ctx-with :result-channel result-channel)
          waiting-ch (start-waiting-for (wait-for-manual-trigger {} ctx))]
      (is (= {:status :timeout} (get-or-timeout waiting-ch :timeout 2000)))
      (reset! (:is-killed ctx) true))) ;; cleanup, make sure the step finishes
  (testing "that the trigger can be killed"
    (let [result-channel (async/chan 100)
          ctx (some-ctx-with :result-channel result-channel)
          waiting-ch (start-waiting-for (wait-for-manual-trigger {} ctx))]
      (reset! (:is-killed ctx) true)
      (is (= {:status :killed} (get-or-timeout waiting-ch :timeout 1500)))))
  (testing "that the parameterized trigger returns the data put into the notification"
    (let [result-channel (async/chan 100)
          ctx (some-ctx-with :result-channel result-channel)
          trigger-id-ch (start-waiting-for-result :trigger-id result-channel)
          waiting-ch (start-waiting-for (parameterized-trigger {} ctx))
          trigger-id (async/<!! trigger-id-ch) ]
      (post-id ctx trigger-id {:foo 1 :bar 2})
      (is (= {:status :success :foo 1 :bar 2} (get-or-timeout waiting-ch))))))
