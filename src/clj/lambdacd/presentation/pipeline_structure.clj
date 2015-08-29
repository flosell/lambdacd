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

(defn- display-type [x]
  (cond
    (is-fn? x) :step
    (and
      (sequential? x)
      (has-child-steps? x)) (or (display-type-by-metadata (first x)) :container)
    (sequential? x) :step
    :else :unknown))

; hacky?
(defn- clear-namespace [s]
  (clojure.string/replace s #"[^/]+/" ""))

(defn display-parameter [x]
  (not (or (symbol? x)
           (sequential? x))))

(defn- display-name [x]
  (if (sequential? x)
    (let [function-name (display-name (first x))
          parameter-values (filter display-parameter (rest x))
          parameters (s/join " " parameter-values)]
      (s/trim (str function-name " " parameters)))
    (clear-namespace (str x))))

(defn- has-dependencies? [f]
  (let [metadata      (meta (find-var f))
        dependant-fns (:depends-on metadata)]
    (not (nil? dependant-fns))))

(declare step-display-representation) ; mutual recursion

(defn- seq-to-display-representations [part parent-step-id]
  (map-indexed #(step-display-representation %2 (conj parent-step-id (inc %1))) part))

(defn- is-simple-step? [x]
  (= :step (display-type x)))

(defn- is-container-step? [x]
  (let [dt (display-type x)]
    (or (= :container dt) (= :parallel x))))

(defn- is-child? [x]
  (or (is-container-step? x) (is-simple-step? x)))

(defn- simple-step-representation [part id]
  {:name (display-name part)
   :type (display-type part)
   :step-id id})

(defn- container-step-representation [part id]
  (let [children (filter is-child? (rest part))]
    {:name (display-name part)
     :type (display-type part)
     :step-id id
     :children (seq-to-display-representations children id)}))

(defn step-display-representation [part id]
  (if (is-simple-step? part)
    (simple-step-representation part id)
    (container-step-representation part id)))


(defn pipeline-display-representation
  ([part]
   (seq-to-display-representations part '())))
