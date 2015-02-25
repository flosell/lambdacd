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
            [lambdacd.core :as core]
            [lambdacd.util :as utils]
            [ring.server.standalone :as ring-server]
            [clojure.tools.logging :as log])
  (:use [lambdacd.control-flow]
        [todopipeline.steps]))


(def pipeline-def
  "the definition of the pipeline as a list of steps that are executed in order."
  `(
    ;; the first step is usually a step that waits for some event to occur, e.g.
    ;; a manual trigger or some change in the repo
    ;; the `either` control-flow element allows us to assemble a new trigger out of the two existing ones:
    ;; wait for either a change in the repository or the manual trigger.
     (either
       lambdacd.manualtrigger/wait-for-manual-trigger
       wait-for-greeting
       ;wait-for-frontend-repo
       ;wait-for-backend-repo
       )
     ;; you could also wait for a repository to change. to try, point the step to a repo you control,
    ;; uncomment this, run and see the magic happen (the first build will immediately run since there is no known state)
    ; wait-for-frontend-repo
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


;; # The Configuration.
;; This is where you define the run-time configuration of LambdaCD. This configuration is passed on to the
;; individual build-steps in the `:config`-value of the context and will be used by the infrastructure and
;; build-steps. :home-dir is the directory where LambdaCD will store all it's internal data that should be persisted
;; over time, such as the last seen revisions of various git-repositories, the build history and so on.

;; For this demonstration, we don't care too much about persisting anything over a longer timeframe so we just use a
;; temp-directory
(def home-dir (utils/create-temp-dir))
(log/info "LambdaCD Home Directory is " home-dir)
(def config { :home-dir home-dir :dont-wait-for-completion true})

;; # Some infrastructure
;; These definitions serve as the entry-points into lambdacd. we need an initialize-function
;; that keeps the pipeline running in background and a ring-handler to be able to access
;; the LambdaCD REST-API and the UI.

;; This function does the work of wiring together everything that's necessary for lambdacd to run
(def pipeline (core/mk-pipeline pipeline-def config))

;; Definitions that are used by the lein-ring plugin to run the application

;; the ring handler
(def app (:ring-handler pipeline))
;; and the function that starts a thread that runs the actual pipeline. 
(def start-pipeline-thread (:init pipeline))

(defn -main [& args]
  (start-pipeline-thread)
  (ring-server/serve app {:open-browser? false
                          :port 8080}))