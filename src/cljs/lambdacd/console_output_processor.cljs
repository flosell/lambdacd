(ns lambdacd.console-output-processor
  (:require [clojure.string :as s]))

(defn- process-carriage-returns [line]
  (->> (clojure.string/split line "\r")
       reverse
       (reduce (fn [final-line previous-chunk]
                 (str final-line (subs previous-chunk (count final-line)))) "")))

(defn- process-backspaces [s]
  (s/join
    (reverse
      (loop [[x & xs] (chars s)
             result (list)]
        (cond
          (nil? x) result
          (= "\b" x) (recur xs (rest result))
          :else (recur xs (conj result x)))))))

(defn process-ascii-escape-characters [s]
  (->> (s/split-lines s)
       (map process-carriage-returns)
       (map process-backspaces)))