(ns smoketest.pipeline
  (:use [lambdacd.steps.control-flow]
        [smoketest.steps])
  (:require [lambdacd.core :as core]
            [lambdacd.util :as utils]))



(def pipeline-def
  `(
     lambdacd.steps.manualtrigger/wait-for-manual-trigger
     wait-for-some-repo
     (with-some-repo
       read-some-value-from-repo)
     (in-parallel
       increment-counter-by-three
       increment-counter-by-two
       use-global-value)
     lambdacd.steps.manualtrigger/wait-for-manual-trigger
  ))

(def config
  {:home-dir (utils/create-temp-dir)})

(def pipeline (core/mk-pipeline pipeline-def config))

(def app (:ring-handler pipeline))
(def start-pipeline-thread (:init pipeline))
