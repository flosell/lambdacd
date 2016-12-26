(ns lambdacd.ui.internal.util-test
  (:require [clojure.test :refer :all]
            [lambdacd.ui.internal.util :refer :all]))

(deftest json-test
         (testing "that a proper ring-json-response is returned"
                  (is (= {:body    "{\"hello\":\"world\"}"
                          :headers {"Content-Type" "application/json;charset=UTF-8"}
                          :status  200} (json { :hello :world })))))
