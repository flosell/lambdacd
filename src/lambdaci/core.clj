(ns lambdaci.core
  (:use [lambdaci.somepipeline.pipeline]
        [lambdaci.dsl]
        [lambdaci.git])
  (gen-class))






(defn -main []
  (run deploymentPipeline))
