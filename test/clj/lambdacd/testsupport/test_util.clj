(ns lambdacd.testsupport.test-util
  (:require [clojure.test :refer :all]
            [lambdacd.internal.execution :as execution]
            [clojure.core.async :as async]
            [clojure.walk :as w]))

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


(defmacro eventually [pred]
  `(loop [count# 0]
     (let [result# ~pred]
        (if (or (not result#) (< count# 10))
          (do
            (Thread/sleep 100)
            (recur (inc count#)))
          result#))))

(defn- tuple? [x]
  (and (vector? x) (= 2 (count x))))

(defn- remove-if-key-value-pair-with-key [k]
  (fn [x]
    (if (and (tuple? x) (= k (first x)))
      nil
      x)))

(defn- remove-with-key
  [k form]
  (w/postwalk (remove-if-key-value-pair-with-key k) form))


(defn without-key [m k]
  (remove-with-key k m))

(defn without-ts
  "strip timestamp information from map to make tests less cluttered with data that's not interesting at the moment"
  [m]
  (-> m
      (without-key :most-recent-update-at)
      (without-key :first-updated-at)))