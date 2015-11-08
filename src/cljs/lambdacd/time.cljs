(ns lambdacd.time
  (:require [cljs-time.format :as format]
            [cljs-time.core :as t]
            [cljsjs.moment]
            [clojure.string :as s]))

(def formatter (format/formatter "yyyy-MM-dd'T'HH:mm:ss.SSSZ"))

(defn parse-time [s]
  (if (nil? s)
    (t/epoch)
    (format/parse formatter s)))

(defn unparse-time [t]
  (format/unparse formatter t))

(defn seconds-between-two-timestamps [t1 t2]
  (if (or (nil? t1) (nil? t2))
    0
    (let [tt1 (if (string? t1) (parse-time t1) t1)
          tt2 (if (string? t2) (parse-time t2) t2)]
      (t/in-seconds (t/interval tt1 tt2)))))

(defn str-if-not-zero [t s]
  (if (zero? t)
    ""
    (str t s)))

(defn format-duration-long [sec]
  (let [dt (t/plus (t/epoch) (t/seconds sec))
        sec-str (str-if-not-zero (t/second dt) "sec")
        min-str (str-if-not-zero (t/minute dt) "min")
        h-str (str-if-not-zero (t/hour dt) "h")
        all (remove s/blank? [h-str min-str sec-str])]
    (s/join " " all)))

(defn format-duration-short [sec]
  (let [dt (t/plus (t/epoch) (t/seconds sec))
        short-format (format/formatter "mm:ss")
        long-format (format/formatter "HH:mm:ss")
        appropriate-format (if (zero? (t/hour dt)) short-format long-format)]
    (format/unparse appropriate-format dt)))

(defn format-ago [s1]
  (when s1 (-> s1
                    (js/moment)
                    (.fromNow))))