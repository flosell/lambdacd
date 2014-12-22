(ns lambdacd.reporters
  "A few reporters to improve test-failure reporting"
  (require [clojure.test :as t]))



(defn pass-fail [b]
  (if b
    :pass
    :fail))

(defmethod t/assert-expr '.endsWith [msg form]
  `(t/do-report {:type (pass-fail ~form) :expected (str "A string ending with `" ~(nth form 2) "`") :actual ~(second form) :message ~msg}))
(defmethod t/assert-expr '.startsWith [msg form]
  `(t/do-report {:type (pass-fail ~form) :expected (str "A starting with `" ~(nth form 2) "`") :actual ~(second form) :message ~msg}))
