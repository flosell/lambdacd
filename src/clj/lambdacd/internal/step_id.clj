(ns lambdacd.internal.step-id
  (:require [lambdacd.util :as util]))

; TODO: make this namespace public

(defn parent-of? [a b]
  (let [cut-off-b (take-last (count a) b)]
    (and
      (not= a b)
      (= a cut-off-b))))

(defn later-than? [a b]
  (let [length (max (count a) (count b))
        a-parents-first (reverse a)
        b-parents-first (reverse b)
        equal-length-a (util/fill a-parents-first length -1)
        equal-length-b (util/fill b-parents-first length -1)
        a-and-b (map vector equal-length-a equal-length-b)
        first-not-equal (first (drop-while (fn [[x y]] (= x y)) a-and-b))
        [x y] first-not-equal]
    (if (nil? first-not-equal)
      (> (count a) (count b))
      (> x y))))

(defn before? [a b]
  (and
    (not= a b)
    (not (later-than? a b))))

(defn child-id [parent-step-id child-number]
  (cons child-number parent-step-id))

(defn root-step-id? [step-id]
  (= 1 (count step-id)))

(defn root-step-id-of [step-id]
  (last step-id))