(ns lambdacd.internal.step-id
  (:require [lambdacd.util :as util]))



(defn later-than? [a b]
  (let [length (max (count a) (count b))
        a-parents-first (reverse a)
        b-parents-first (reverse b)
        equal-length-a (util/fill a-parents-first length -1)
        equal-length-b (util/fill b-parents-first length -1)
        a-and-b (map vector equal-length-a equal-length-b)
        first-not-equal (first (take-while (fn [[x y]] (not= x y)) a-and-b))
        [x y] first-not-equal]
    (if (nil? first-not-equal)
      (> (count a) (count b))
      (> x y))))

(defn before? [a b]
  (and
    (not (= a b))
    (not (later-than? a b))))