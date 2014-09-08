(ns todopipeline.pipeline
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
    some-failing-step
    some-step-that-cant-be-reached
  ))
