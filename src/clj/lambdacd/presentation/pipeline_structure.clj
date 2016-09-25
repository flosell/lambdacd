(ns lambdacd.presentation.pipeline-structure
  "this namespace is responsible for converting the pipeline
  into a nice, map-format that we can use to display the pipeline
  in a UI"
  (:require [clojure.string :as s]))

(defn- metadata [fun]
  (meta (find-var fun)))

(defn- display-type-by-metadata [fun]
  (:display-type (metadata fun)))

(declare is-child?)

(defn- has-child-steps? [x]
  (some is-child? (rest x)))

(defn- is-nested-with-children [x]
  (and
    (sequential? x)
    (has-child-steps? x)))

(defn- display-type [x]
  (cond
    (nil? x) :unknown
    (clojure.test/function? x) (or (display-type-by-metadata x) :step)
    (is-nested-with-children x) (or (display-type-by-metadata (first x)) :container)
    (sequential? x) :step
    :else :unknown))

; hacky?
(defn- clear-namespace [s]
  (clojure.string/replace s #"[^/]+/" ""))

(defn- displayable-parameter? [x]
  (not (or (symbol? x)
           (nil? x)
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
  (let [visible-args (visible-args-bitmap fun)
        displayable-args (map displayable-parameter? params)
        ; pad to make sure varargs don't cause problems
        args-to-display (map both-true? (pad visible-args true) displayable-args)
        parameter-values (->> (map vector args-to-display params)
                              (filter first)
                              (map second))]
    parameter-values))

(defn- is-alias-fun? [fun]
  (:is-alias (metadata fun)))

(defn- display-name [x]
  (if (sequential? x)
    (let [fun (first x)
          params (rest x)
          parts (concat [(display-name fun)] (parameter-values fun params))]
      (if (is-alias-fun? fun)
        (first params)
        (s/join " " parts)))
    (clear-namespace (str x))))

(defn- has-dependencies? [x]
  (let [f                         (if (sequential? x) (first x) x)
        depends-on-previous-steps (:depends-on-previous-steps (metadata f))]
    (boolean depends-on-previous-steps)))

(declare step-display-representation)                       ; mutual recursion

(defn- seq-to-display-representations [parent-step-id part]
  (map-indexed #(step-display-representation %2 (conj parent-step-id (inc %1))) (remove nil? part)))

(defn- is-container-step? [x]
  (let [dt (display-type x)]
    (and
      (not (nil? x)))
    (or (= :container dt) (= :parallel dt))))

(defn- has-display-type? [x]
  (not= :unknown (display-type x)))

(defn- is-child? [x]
  (has-display-type? x))

(defn- simple-step-representation [part id]
  {:name             (display-name part)
   :type             (display-type part)
   :has-dependencies (has-dependencies? part)
   :step-id          id})

(defn- is-alias-part? [part]
  (and
    (sequential? part)
    (is-alias-fun? (first part))))

(defn- container-step-representation [part id]
  (let [children-representations (->> part
                                      (rest)
                                      (filter is-child?)
                                      (seq-to-display-representations id))]
    (if (is-alias-part? part)
      (assoc (first children-representations) :name (display-name part))
      {:name             (display-name part)
       :type             (display-type part)
       :step-id          id
       :has-dependencies false
       :children         children-representations})))

(defn step-display-representation [part id]
  (if (is-container-step? part)
    (container-step-representation part id)
    (simple-step-representation part id)))


(defn pipeline-display-representation
  ([part]
   (seq-to-display-representations '() part)))
