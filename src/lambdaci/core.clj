(ns lambdaci.core
  (:require [todopipeline.pipeline :as todo])
  (:use [lambdaci.dsl])
  (gen-class))


(defn -main []
  (run todo/pipeline)
  (shutdown-agents)) ; workaround to make program terminate immediately instead of waiting for futures (used by sh) to shut down

