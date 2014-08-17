(ns lambdaci.visual
  (:require [todopipeline.pipeline :as todo]
            [lambdaci.dsl :as dsl]))

(defn display-type [fun]
  (if (= `dsl/in-parallel fun)
    :parallel
    :step))

; hacky?
(defn clear-namespace [s]
  (clojure.string/replace s #"[^/]+/" ""))

(defn display-name [fun]
  (clear-namespace (str fun)))
