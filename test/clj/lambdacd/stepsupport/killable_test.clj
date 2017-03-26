(ns lambdacd.stepsupport.killable-test
  (:require [clojure.test :refer :all]
            [lambdacd.stepsupport.killable :refer :all]
            [lambdacd.testsupport.data :refer [some-ctx some-ctx-with]]
            [clojure.core.async :as async]))

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
