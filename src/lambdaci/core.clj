(ns lambdaci.core
  (:use [todopipeline.pipeline]
        [lambdaci.dsl]
        [lambdaci.git])
  (gen-class))






(defn -main []
  (run deploymentPipeline))
