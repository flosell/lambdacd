(ns lambdaci.visual
  (:require [todopipeline.pipeline :as todo]
            [lambdaci.dsl :as dsl]))

(defn display-type [fun]
  (if (= `dsl/in-parallel fun)
    :parallel
    (if (= `dsl/in-cwd fun)
      :container
      (if (not (or (string? fun) (map? fun)))
        :step
        :unknown))))

(defn- is-step? [step]
  (= :step (display-type step)))

; hacky?
(defn- clear-namespace [s]
  (clojure.string/replace s #"[^/]+/" ""))

(defn display-name [fun]
  (clear-namespace (str fun)))

; FIXME this is from from clojure.test
(defn- is-fn?
  "Returns true if argument is a function or a symbol that resolves to
  a function (not a macro)."
  {:added "1.1"}
  [x]
  (if (symbol? x)
    (when-let [v (resolve x)]
      (when-let [value (var-get v)]
        (and (fn? value)
             (not (:macro (meta v))))))
    (fn? x)))

(declare display-representation) ; display-representatn and display-representation-for-seq are mutually recursive

(defn display-representation-for-seq [part]
  (let [f (first part)
          r (filter is-step? (rest part))]
    (if (is-step? f)
      (map display-representation part)
      {:name (display-name f)
       :type (display-type f)
       :children (map display-representation r)})))

(defn display-representation [part]
;;  (println (str "rep for" part))
  (if (seq? part)
    (display-representation-for-seq part)
    {:name (display-name part) :type (display-type part)}))
