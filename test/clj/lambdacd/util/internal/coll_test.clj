(ns lambdacd.util.internal.coll-test
  (:require [clojure.test :refer :all]
            [lambdacd.util.internal.coll :refer :all]))

(deftest fill-test
         (testing "that we can fill up a sequence to a certain length"
                  (is (= [1 2 3 -1 -1] (fill [1 2 3] 5 -1))))
         (testing "that a collection is left just as it was if it is already longer than the desired length"
                  (is (= [1 2 3] (fill [1 2 3] 2 -1)))
                  (is (= [1 2 3] (fill [1 2 3] 3 -1)))))
