(ns lambdacd.console-output-processor
  (:require [clojure.string :as s]
            [cljsjs.ansiparse]))

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

(defn clean-up-text [s]
  (->> (s/split-lines s)
       (map process-carriage-returns)
       (map process-backspaces)
       (s/join "\n")))

(defn- de-ansify [s]
  (if (= "" s)
    [{:text s}]
    (js->clj (js/ansiparse s) :keywordize-keys true)))

(defn split-fragments-on-newline [fragment]
  (cond
    (= "\n" (:text fragment)) [:newline]
    (= "" (:text fragment)) [fragment]
    :else (->> (s/split-lines (:text fragment))
               (map #(assoc fragment :text %))
               (interpose :newline))))

(defn- partition-by-newline [c]
  (let [by-k #(not= :newline %)]
    (filter #(not= [:newline] %)
            (partition-by by-k c))))


(defn process-ascii-escape-characters [s]
  (->> (clean-up-text s)
       (de-ansify)
       (mapcat split-fragments-on-newline)
       (partition-by-newline)))