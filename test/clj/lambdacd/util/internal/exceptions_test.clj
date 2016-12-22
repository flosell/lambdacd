(ns lambdacd.util.internal.exceptions-test
  (:require [clojure.test :refer :all]
            [lambdacd.util.internal.exceptions :refer [stacktrace-to-string]]))

(deftest stacktrace-to-string-test
  (testing "that we can convert an exception into a proper string"
    (let [result (stacktrace-to-string (Exception. "some error"))]
      (is (.contains result "some error"))
      (is (.contains result "exceptions_test.clj")))))
