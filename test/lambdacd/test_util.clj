(ns lambdacd.test-util
  (:require [clojure.test :refer :all]
            [lambdacd.execution :refer :all]
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

(defmacro with-private-fns [[ns fns] & tests]
  "Refers private fns from ns and runs tests in context."
  `(let ~(reduce #(conj %1 %2 `(ns-resolve '~ns '~%2)) [] fns)
     ~@tests))


(defmacro atom-history-for [value-atom body]
  `(let [history-atom# (atom [])]
     (add-watch ~value-atom :foo (fn [key# ref# old# new#]
                                         (if (not= old# new#)
                                           (swap! history-atom# conj new#))))
     ~body
     @history-atom#))