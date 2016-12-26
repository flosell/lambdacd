(ns lambdacd.util.internal.sugar-test
  (:require [clojure.test :refer :all]
            [lambdacd.util.internal.sugar :refer :all]))

(deftest parse-int-test
  (testing "that we can parse integers"
    (is (= 42 (parse-int "42")))
    (is (= -1 (parse-int "-1")))
    (is (thrown? NumberFormatException (parse-int "foo")))))

