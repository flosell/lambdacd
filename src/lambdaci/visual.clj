(ns lambdaci.visual
  (:require [todopipeline.pipeline :as todo]
            [lambdaci.dsl :as dsl]))

(defn display-type [fun]
  (if (= `dsl/in-parallel fun)
    :parallel
    :step))
