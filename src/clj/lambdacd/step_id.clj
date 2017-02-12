(ns lambdacd.step-id
  "Utility functions to deal with the logic behind step ids:
  step-ids are a sequence of numbers that represent the structure of the pipeline.

  Examples:

  * `[1]` is the first step of the pipeline
  * `[2 1]` is the second child of the first step of the pipeline.

  For detailed examples on these functions, refer to their [tests](https://github.com/flosell/lambdacd/blob/master/test/clj/lambdacd/step_id_test.clj)."
  (:require [lambdacd.util.internal.coll :as coll-util]))

(defn parent-of?
  "Returns true if the steps the given step-ids refer to are parent and child."
  [parent child]
  (let [cut-off-b (take-last (count parent) child)]
    (and
      (not= parent child)
      (= parent cut-off-b))))

(defn direct-parent-of?
  "Returns true if the steps the given step-ids refer to are parent and immediate child."
  [parent child]
  (and
    (parent-of? parent child)
    (= (inc (count parent))
       (count child))))

(defn later-than?
  "Returns true if for two steps, the step with id a is executed after b in the pipeline."
  [a b]
  (let [length (max (count a) (count b))
        a-parents-first (reverse a)
        b-parents-first (reverse b)
        equal-length-a (coll-util/fill a-parents-first length -1)
        equal-length-b (coll-util/fill b-parents-first length -1)
        a-and-b (map vector equal-length-a equal-length-b)
        first-not-equal (first (drop-while (fn [[x y]] (= x y)) a-and-b))
        [x y] first-not-equal]
    (if (nil? first-not-equal)
      (> (count a) (count b))
      (> x y))))

(defn before?
  "Returns true if for two steps, the step with id b is executed after a in the pipeline."
  [a b]
  (and
    (not= a b)
    (not (later-than? a b))))

(defn child-id
  "Returns a step id for the `child-number`th child of the step with id `parent-step-id`."
  [parent-step-id child-number]
  (cons child-number parent-step-id))

(defn root-step-id?
  "Returns true if the step-id belongs to a root-step, i.e. a step with no parents."
  [step-id]
  (= 1 (count step-id)))

(defn root-step-id-of
  "Returns the id of the root-parent of the step with the given id."
  [step-id]
  (last step-id))
