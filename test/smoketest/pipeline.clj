(ns smoketest.pipeline
  (:use [lambdacd.control-flow]
        [smoketest.steps])
  (:require [lambdacd.server :as server]
            [lambdacd.execution :as execution]
            [lambdacd.core :as core]))



(def pipeline-def
  `(
     lambdacd.manualtrigger/wait-for-manual-trigger
     (in-parallel
       increment-counter-by-three
       increment-counter-by-two)
     lambdacd.manualtrigger/wait-for-manual-trigger
  ))


(def pipeline (core/mk-pipeline pipeline-def))

(def app (:ring-handler pipeline))
(def start-pipeline-thread (:init pipeline))