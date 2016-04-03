(ns lambdacd.runners-test
  (:require [clojure.test :refer :all]
            [lambdacd.runners :refer :all]
            [lambdacd.testsupport.test-util :refer [call-with-timeout start-waiting-for get-or-timeout wait-for]]
            [lambdacd.testsupport.data :refer [some-ctx]]
            [lambdacd.event-bus :as event-bus]))

(deftest while-not-stopped-test
  (testing "that it can be stopped"
    (let [ctx (some-ctx)
          running (atom false)
          handle (start-waiting-for (while-not-stopped ctx (reset! running true)))]
      (wait-for @running)
      ; we expect it doesn't stop
      (is (= {:status :timeout} (get-or-timeout handle :timeout 500)))
      ; we stop it
      (stop-runner ctx)
      ; it stops
      (is (not= {:status :timeout} (get-or-timeout handle :timeout 200))))))