(ns todopipeline.pipeline
  (:require [lambdacd.server :as server])
  (:use [lambdacd.execution]
        [lambdacd.control-flow]
        [todopipeline.steps]))



(def pipeline
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


(def app (server/ui-for pipeline))
(defn start-pipeline-thread [] (server/start-pipeline-thread pipeline))