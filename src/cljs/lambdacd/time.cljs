(ns lambdacd.time
  (:require [cljs-time.format :as format]
            [cljs-time.core :as t]
            [clojure.string :as s]))

(defn parse-time [s]
  (if (nil? s)
    (t/epoch)
    (format/parse s)))

(defn seconds-between-two-timestamps [t1 t2]
  (t/in-seconds (t/interval t1 t2)))

(defn str-if-not-zero [t s]
  (if (zero? t)
    ""
    (str t s)))

(defn format-duration-in-seconds [s]
  (let [dt (t/plus (t/epoch) (t/seconds s))
        sec-str (str-if-not-zero (t/second dt) "sec")
        min-str (str-if-not-zero (t/minute dt) "min")
        h-str (str-if-not-zero (t/hour dt) "h")
        all (filter (complement s/blank?) [h-str min-str sec-str])]
    (s/join " " all)))


(defn format-duration [s1 s2]
  (let [t1 (parse-time s1)
        t2 (parse-time s2)
        sec (seconds-between-two-timestamps t1 t2)]
    (format-duration-in-seconds sec)))