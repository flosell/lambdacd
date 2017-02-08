(ns lambdacd.presentation.pipeline-structure
  "This namespace is responsible for converting the pipeline
  into a nice map-format that we can use to display the pipeline
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

(defn pad ; TODO: should be private
  "DEPRECATED"
  {:deprecated "0.13.1"}
  [coll val]
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

(declare step-display-representation-internal)                       ; mutual recursion

(defn- seq-to-display-representations [parent-step-id include-alias? part]
  (map-indexed #(step-display-representation-internal %2 (conj parent-step-id (inc %1)) include-alias?) (remove nil? part)))

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

(defn- container-step-representation [part id include-alias?]
  (let [children-representations (->> part
                                      (rest)
                                      (filter is-child?)
                                      (seq-to-display-representations id include-alias?))]
    (if (is-alias-part? part)
      (if include-alias?
        {:name             "alias"
         :type             (display-type part)
         :step-id          id
         :has-dependencies false
         :children         [(assoc (first children-representations) :name (display-name part))]}
        (assoc (first children-representations) :name (display-name part)))
      {:name             (display-name part)
       :type             (display-type part)
       :step-id          id
       :has-dependencies false
       :children         children-representations})))

(defn step-display-representation-internal
  ; TODO: should be private
  "DEPRECATED"
  {:deprecated "0.13.1"}
  [part id include-alias?]
  (if (is-container-step? part)
    (container-step-representation part id include-alias?)
    (simple-step-representation part id)))

(defn step-display-representation
  "Takes a part of a pipeline and its step-id and returns a map-representation with data about this part of the pipeline:
  ```clojure
  > (step-display-representation `do-stuff `(1)])
  {:name \"do-stuff\" :type :step :step-id '(1) :has-dependencies false}
  ```"
  [part id]
  (step-display-representation-internal part id false))

(defn pipeline-display-representation
  "Takes a pipeline-structure and returns a nested list of step representations (i.e. the result of `step-display-representation`):
  ```clojure
  > (pipeline-display-representation `((in-parallel
                                         (in-cwd \"foo\"
                                                 do-stuff)
                                         (in-cwd \"bar\"
                                                 do-other-stuff)))

  [{:name \"in-parallel\"
    :type :parallel
    :step-id '(1)
    :has-dependencies false
    :children
    [{:name \"in-cwd foo\"
      :type :container
      :step-id '(1 1)
      :has-dependencies false
      :children [{:name \"do-stuff\" :type :step :step-id '(1 1 1) :has-dependencies false}]}
     {:name \"in-cwd bar\"
      :type :container
      :step-id '(2 1)
      :has-dependencies false
      :children [{:name \"do-other-stuff\" :type :step :step-id '(1 2 1) :has-dependencies true}]}]}]
  ```"
  ([part]
   (seq-to-display-representations '() false part)))

(defn flatten-pipeline-representation
  "Takes a pipeline-representation as returned by `pipeline-display-representation` (which is hierarchical, containing
  only representations for the root-steps which then contain the representation of their children) and returns a collection
  of pipeline representations, this time containing all child steps as well."
  [reps]
  (flatten
    (for [rep reps]
      (if (:children rep)
        (conj (flatten-pipeline-representation (:children rep)) rep)
        [rep]))))

(defn step-display-representation-by-step-id
  "Returns a information about step (as returned by `step-display-representation`) for a specific step (identified by a step-id) in a pipeline:
  ```clojure
  > (step-display-representation-by-step-id `(do-stuff
                                                do-other-stuff
                                                do-stuff-that-has-a-different-display-type) `(1))
  {:name \"do-stuff\" :type :step :step-id '(1) :has-dependencies false}
  ```"
  [pipeline-def step-id]
  (->> (seq-to-display-representations '() true pipeline-def)
       (flatten-pipeline-representation)
       (filter #(= (:step-id %) step-id))
       (first)))
