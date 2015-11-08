(ns lambdacd.testdata
  (:require [lambdacd.time :as time]
            [cljs-time.core :as t]))

(def time-start (t/now))
(def time-after-ten-sec (t/plus time-start (t/seconds 10)))

(def some-build-step
  {:name "some-step"
   :type "step"
   :step-id [1 2 3]
   :children []
   :result {:status "success"
            :out "hello world"
            :first-updated-at (time/unparse-time time-start)
            :most-recent-update-at (time/unparse-time time-after-ten-sec)}})

(defn with-name [step name]
  (assoc step :name name))

(defn with-step-id [step step-id]
  (assoc step :step-id step-id))

(defn with-type [step name]
  (assoc step :type name))

(defn with-output [step output]
  (assoc step :result {:status "success" :out output}))

(defn with-children [step children]
  (assoc step :children children))

(defn with-most-recent-update [step ts]
  (assoc-in step [:result :most-recent-update-at] ts))

(defn with-first-update-at [step ts]
  (assoc-in step [:result :first-updated-at] ts))