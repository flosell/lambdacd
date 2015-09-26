(ns lambdacd.time-test
  (:require [cljs.test :refer-macros [deftest is testing run-tests]]
            [dommy.core :refer-macros [sel sel1]]
            [cljs-time.core :as cljs-time]
            [cljs-time.extend] ; this makes equality-comparisons work
            [lambdacd.time :as time]))


(deftest parse-time-test
  (testing "that we can parse a properly formatted time-string"
    (is (= (cljs-time/date-time 2015 5 16 19 36 20 214) (time/parse-time "2015-05-16T19:36:20.214Z"))))
  (testing "that it returns epoch if no date is given"
           (is (= (cljs-time/epoch) (time/parse-time nil)))))


(deftest seconds-between-two-timestamps
  (testing "that the same timestamp is 0 seconds apart"
    (is (= 0 (time/seconds-between-two-timestamps
               (cljs-time/date-time 2015 5 16 19 36 0 0)
               (cljs-time/date-time 2015 5 16 19 36 0 0)))))
  (testing "that we can calculate the difference when the two timestamps are only seconds away from each other"
           (is (= 3 (time/seconds-between-two-timestamps
                      (cljs-time/date-time 2015 5 16 19 36 00)
                      (cljs-time/date-time 2015 5 16 19 36 03)))))
  (testing "that we can calculate the difference when the two timestamps a few minutes from each other"
           (is (= 123 (time/seconds-between-two-timestamps
                      (cljs-time/date-time 2015 5 16 19 36 00)
                      (cljs-time/date-time 2015 5 16 19 38 03))))))

(deftest format-duration-in-seconds-test
         (testing "that we can format the duration between two timestamps"
                  (is (= "10sec" (time/format-duration-in-seconds
                                          10)))
                  (is (= "1min 15sec" (time/format-duration-in-seconds
                                    75)))
                  (is (= "3h 15sec" (time/format-duration-in-seconds
                                              (+ 15 (* 3 60 60)))))))

(deftest format-duration-test
         (testing "that we can format the duration between two timestamps that are represented as strings"
                  (is (= "3h 10min 15sec" (time/format-duration
                                      "2015-05-16T19:00:00.214Z"
                                      "2015-05-16T22:10:15.214Z")))))