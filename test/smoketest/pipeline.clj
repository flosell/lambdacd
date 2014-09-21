(ns smoketest.pipeline
  (:use [lambdacd.control-flow]
        [smoketest.steps])
  (:require [lambdacd.server :as server]
            [lambdacd.execution :as execution]))



(def pipeline
  `(
     lambdacd.manualtrigger/wait-for-manual-trigger
     (in-parallel
       increment-counter-by-three
       increment-counter-by-two)
     lambdacd.manualtrigger/wait-for-manual-trigger
  ))


(def app (server/ui-for pipeline))
(defn start-pipeline-thread [] (execution/start-pipeline-thread pipeline))