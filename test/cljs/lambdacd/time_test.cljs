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
  (testing "that if one of the timestamps is nil, it evaluates to 0 seconds"
    (is (= 0 (time/seconds-between-two-timestamps
               nil
               (cljs-time/date-time 2015 5 16 19 36 0 0))))
    (is (= 0 (time/seconds-between-two-timestamps
               (cljs-time/date-time 2015 5 16 19 36 0 0)
               nil))))
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
                 (cljs-time/date-time 2015 5 16 19 38 03)))))
  (testing "that optionally, we can also pass in timestamps as strings as they come from json"
    (is (= 60 (time/seconds-between-two-timestamps
               "2015-05-16T19:35:00.000Z"
               (cljs-time/date-time 2015 5 16 19 36 0 0))))
    (is (= 15 (time/seconds-between-two-timestamps
               (cljs-time/date-time 2015 5 16 19 36 0 0)
               "2015-05-16T19:36:15.000Z")))))

(deftest format-duration-long
  (testing "that we can format the duration between two timestamps"
    (is (= "10sec" (time/format-duration-long
                     10)))
    (is (= "1min 15sec" (time/format-duration-long
                          75)))
    (is (= "3h 15sec" (time/format-duration-long
                        (+ 15 (* 3 60 60)))))))

(deftest format-duration-short
  (testing "that we can format the duration between two timestamps"
    (is (= "00:10" (time/format-duration-short
                     10)))
    (is (= "01:15" (time/format-duration-short
                     75)))
    (is (= "03:00:15" (time/format-duration-short
                        (+ 15 (* 3 60 60)))))))
