(ns todopipeline.pipeline
  (:require [lambdacd.server :as server]
            [lambdacd.execution :as execution]
            [lambdacd.core :as core])
  (:use [lambdacd.control-flow]
        [todopipeline.steps]))



(def pipeline-def
  `(
    ; wait-for-frontend-repo
    lambdacd.manualtrigger/wait-for-manual-trigger
    (in-parallel
      (with-frontend-git
        client-package)
      (with-backend-git
        server-test
        server-package))
    (in-cwd "/tmp/mockrepo" ; the publish-scripts copy deploy-scripts and artifacts into this directory
      client-deploy-ci
      server-deploy-ci)
    ;; waiting again because the we are buggy and still want to see the successful server-deployment... TODO: remove when bug resolved
    lambdacd.manualtrigger/wait-for-manual-trigger
    some-failing-step
    some-step-that-cant-be-reached
  ))

(def pipeline (core/mk-pipeline pipeline-def))

(def app (:ring-handler pipeline))
(def start-pipeline-thread (:init pipeline))