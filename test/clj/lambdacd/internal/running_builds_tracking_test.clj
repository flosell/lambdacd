(ns lambdacd.internal.running-builds-tracking-test
  (:require [clojure.test :refer :all]
            [lambdacd.internal.running-builds-tracking :refer :all]
            [lambdacd.testsupport.data :refer [some-ctx some-ctx-with]]
            [lambdacd.testsupport.test-util :refer [wait-for]]
            [lambdacd.event-bus :as event-bus]))

(deftest running-builds-tracker
  (testing "that it adds running builds and step-ids when steps start runnning and removes them if they stop"
    (let [ctx (initialize-running-builds-tracking (some-ctx))]

      (event-bus/publish ctx :step-started {:step-id [1] :build-number 1})
      (event-bus/publish ctx :step-started {:step-id [2 2] :build-number 2})

      (wait-for (= 2 (count @(:started-steps ctx))))
      (is (= #{{:step-id [1] :build-number 1}
               {:step-id [2 2] :build-number 2}} @(:started-steps ctx)))

      (event-bus/publish ctx :step-finished {:step-id [2 2] :build-number 2 :final-result {:foo :bar}})

      (wait-for (= 1 (count @(:started-steps ctx))))

      (is (= #{{:step-id [1] :build-number 1}} @(:started-steps ctx))))))
