(ns lambdacd.presentation
  "this namespace is responsible for converting the pipeline
  into a nice, map-format that we can use to display the pipeline
  in a UI"
  (:require [lambdacd.execution :as execution]))


(defn- is-fn? [fun]
  (not (or (string? fun) (map? fun)))) ; hackedyhack...

(defn- display-type [fun]
  (if (symbol? fun)
    (let [metadata-dt (:display-type (meta (find-var fun)))]
    (if (nil? metadata-dt)
      (if (is-fn? fun)
        :step
        :unknown)
      metadata-dt))
    (if (sequential? fun)
      :step
      :unknown)))

(defn- is-step? [step]
  (= :step (display-type step)))

; hacky?
(defn- clear-namespace [s]
  (clojure.string/replace s #"[^/]+/" ""))

(defn- display-name [fun]
  (clear-namespace (str fun)))

(declare display-representation) ; display-representatn and display-representation-for-seq are mutually recursive

(defn- display-representation-for-seq [part]
  (let [f (first part)
          r (filter is-step? (rest part))]
    (if (is-step? f)
      (map display-representation part)
      {:name (display-name f)
       :type (display-type f)
       :children (map display-representation r)})))

(defn display-representation [part]
  (if (sequential? part)
    (display-representation-for-seq part)
    {:name (display-name part) :type (display-type part)}))
