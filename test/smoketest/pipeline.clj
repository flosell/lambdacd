(ns smoketest.pipeline
  (:require [lambdacd.server :as server])
  (:use [lambdacd.execution]
        [lambdacd.control-flow]
        [smoketest.steps]))



(def pipeline
  `(
     lambdacd.manualtrigger/wait-for-manual-trigger
     (in-parallel
       increment-counter-by-three
       increment-counter-by-two)
     lambdacd.manualtrigger/wait-for-manual-trigger
  ))


(def app (server/ui-for pipeline))
(defn start-pipeline-thread [] (server/start-pipeline-thread pipeline))