;; # The pipeline definiton.
;; This namespace contains the definition of the continuous delivery pipeline,
;; i.e. the order and structure in which build steps are executed. The
;; steps themselves are specified in their own namespace to keep those two things
;; seperate.
;;
;; Also in this namespace ring-handler and startup functions that form the entrypoints
;; into the system.

(ns todopipeline.pipeline
  (:require [lambdacd.server :as server]
            [lambdacd.execution :as execution]
            [lambdacd.core :as core])
  (:use [lambdacd.control-flow]
        [todopipeline.steps]))


(def pipeline-def
  "the definition of the pipeline as a list of steps that are executed in order."
  `(
    ;; the first step is usually a step that waits for some event to occur, e.g.
    ;; a manual trigger or some change in the repo
    lambdacd.manualtrigger/wait-for-manual-trigger
    ;; this step executes his child-steps (the arguments after the in-parallel) in parallel and waits
    ;; until all of them are done. if one of them fails, the whole step fails.
    (in-parallel
      ;; these child steps do some actual work with the checked out git repo
      (with-frontend-git
        client-package)
      (with-backend-git
        server-test
        server-package))
    ;; the package-scripts copy deploy-scripts and artifacts into the mockrepo directory,
    ;; execute the depoy-steps from there
    (in-cwd "/tmp/mockrepo"
      client-deploy-ci
      server-deploy-ci)

    ;; now we want the build to fail, just to show it's working.
    some-failing-step
    some-step-that-cant-be-reached
  ))


;; # Some infrastructure
;; These definitions serve as the entry-points into lambdacd. we need an initialize-function
;; that keeps the pipeline running in background and a ring-handler to be able to access
;; the LambdaCD REST-API and the UI.

;; This function does the work of wiring together everything that's necessary for lambdacd to run
(def pipeline (core/mk-pipeline pipeline-def))

;; Definitions that are used by the lein-ring plugin to run the application

;; the ring handler
(def app (:ring-handler pipeline))
;; and the function that starts a thread that runs the actual pipeline. 
(def start-pipeline-thread (:init pipeline))