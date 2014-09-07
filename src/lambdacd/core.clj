(ns lambdacd.core
  (:require [todopipeline.pipeline :as todo]
            [clojure.data.json :as json :only [write-str]])
  (:use [lambdacd.execution])
  (gen-class))


(defn -main []
  (run todo/pipeline)
  (shutdown-agents)) ; workaround to make program terminate immediately instead of waiting for futures (used by sh) to shut down



