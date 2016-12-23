(ns lambdacd.util.internal.sugar)

(def not-nil? (complement nil?))
(defn parse-int [int-str]
  (Integer/parseInt int-str))
