(ns lambdacd.test-util
  (:require [clojure.test :refer :all]
            [lambdacd.dsl :refer :all]
            [clojure.core.async :as async]))

(defmacro my-time
  "measure the time a function took to execute"
  [expr]
  `(let [start# (. System (nanoTime))]
     ~expr
     (/ (double (- (. System (nanoTime)) start#)) 1000000.0)))


(defn absolute-difference ^double [^double x ^double y]
  (Math/abs (double (- x y))))

(defn close? [tolerance x y]
  (< (absolute-difference x y) tolerance))
