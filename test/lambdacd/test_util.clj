(ns lambdacd.test-util
  (:require [clojure.test :refer :all]
            [lambdacd.execution :as execution]
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


(defn history-for-atom [value-atom]
  (let [history-atom (atom [])]
    (add-watch value-atom :foo (fn [_ _ old new]
                                (if (not= old new)
                                  (swap! history-atom conj new))))
    history-atom))

(defmacro atom-history-for [value-atom body]
  `(let [history-atom# (history-for-atom ~value-atom)]
     ~body
     @history-atom#))

(defn result-channel->map [ch]
  (async/<!!
    (async/go-loop [last {}]
      (if-let [[key value] (async/<! ch)]
        (let [new (assoc last key value)]
          (if (#'execution/is-finished key value)
            new
            (recur new)))
        last))))