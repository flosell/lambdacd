(ns lambdaci.core
  (:use [todopipeline.pipeline]
        [lambdaci.dsl])
  (gen-class))


(defn -main []
  (run client-pipeline)
  (shutdown-agents)) ; workaround to make program terminate immediately instead of waiting for futures (used by sh) to shut down
