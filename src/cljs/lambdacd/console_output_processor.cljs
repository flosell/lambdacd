(ns lambdacd.console-output-processor
  (:require [clojure.string :as s]))

(defn- process-carriage-returns [s]
  (s/replace s #".*\r" ""))

(defn process-ascii-escape-characters [s]
  (->> (s/split-lines s)
      (map process-carriage-returns)))