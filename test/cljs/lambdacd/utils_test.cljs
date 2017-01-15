(ns lambdacd.utils-test
  (:require [cljs.test :refer-macros [deftest is testing run-tests]]
            [lambdacd.utils :as utils]))

(deftest stringify-keys-test
  (testing "that keywords in maps are properly stringified for rendering"
    (is (= { ":foo" 42} (utils/stringify-keys {:foo 42}))))
  (testing "that strange keywords with namespaces are supported (#100)"
    (is (= { ":refs/heads/master" "some-sha"} (utils/stringify-keys { (keyword "refs/heads/master") "some-sha" })))))
