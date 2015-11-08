(ns lambdacd.presentation.pipeline-structure
  "this namespace is responsible for converting the pipeline
  into a nice, map-format that we can use to display the pipeline
  in a UI"
  (:require [clojure.string :as s]))


(defn- is-fn? [fun]
  (not (or (string? fun)
           (map? fun)
           (sequential? fun)
           (integer? fun)
           (keyword? fun)
           (float? fun)))) ; hackedyhack...

(defn- display-type-by-metadata [fun]
  (:display-type (meta (find-var fun))))

(declare is-child?)

(defn- has-child-steps? [x]
  (some is-child? (rest x)))

(defn- is-nested-with-children [x]
  (and
    (sequential? x)
    (has-child-steps? x)))

(defn- display-type [x]
  (cond
    (is-fn? x) :step
    (is-nested-with-children x) (or (display-type-by-metadata (first x)) :container)
    (sequential? x) :step
    :else :unknown))

; hacky?
(defn- clear-namespace [s]
  (clojure.string/replace s #"[^/]+/" ""))

(defn displayable-parameter? [x]
  (not (or (symbol? x)
           (sequential? x))))

(defn pad [coll val]
  (concat coll (repeat val)))

(defn- visible-args-bitmap [fun]
  (let [arglist (first (:arglists (meta (find-var fun))))
        bitmap (map #(not (:hide (meta %))) arglist)]
    bitmap))

(defn- both-true? [a b]
  (and a b))

(defn- parameter-values [fun params]
  ;; not supported at the moment:
  ;; * declare variable arguments as hidden
  ;; * declare arguments as hidden in function with more than one argument list (first one is always taken)
  (let [visible-args     (visible-args-bitmap fun)
        displayable-args (map displayable-parameter? params)
        ; pad to make sure varargs don't cause problems
        args-to-display  (map both-true? (pad visible-args true) displayable-args)
        parameter-values (->> (map vector args-to-display params)
                              (filter first)
                              (map second))]
    parameter-values))

(defn- display-name [x]
  (if (sequential? x)
    (let [fun    (first x)
          params (rest x)
          parts  (concat [(display-name fun)] (parameter-values fun params))]
      (s/join " " parts))
    (clear-namespace (str x))))

(defn- has-dependencies? [x]
  (let [f (if (sequential? x) (first x) x)
        metadata (meta (find-var f))
        depends-on-previous-steps (:depends-on-previous-steps metadata)]
    (boolean depends-on-previous-steps)))

(declare step-display-representation) ; mutual recursion

(defn- seq-to-display-representations [part parent-step-id]
  (map-indexed #(step-display-representation %2 (conj parent-step-id (inc %1))) part))

(defn- is-simple-step? [x]
  (= :step (display-type x)))

(defn- is-container-step? [x]
  (let [dt (display-type x)]
    (or (= :container dt) (= :parallel x))))

(defn- is-child? [x]
  (or (is-container-step? x) (is-simple-step? x) (is-nested-with-children x)))

(defn- simple-step-representation [part id]
  {:name (display-name part)
   :type (display-type part)
   :has-dependencies (has-dependencies? part)
   :step-id id})

(defn- container-step-representation [part id]
  (let [children (filter is-child? (rest part))]
    {:name (display-name part)
     :type (display-type part)
     :step-id id
     :has-dependencies false
     :children (seq-to-display-representations children id)}))

(defn step-display-representation [part id]
  (if (is-simple-step? part)
    (simple-step-representation part id)
    (container-step-representation part id)))


(defn pipeline-display-representation
  ([part]
   (seq-to-display-representations part '())))
