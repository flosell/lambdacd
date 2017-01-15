;; # The pipeline definiton.
;; This namespace contains the definition of the continuous delivery pipeline,
;; i.e. the order and structure in which build steps are executed. The
;; steps themselves are specified in their own namespace to keep those two things
;; seperate.
;;
;; Also in this namespace ring-handler and startup functions that form the entrypoints
;; into the system.

(ns todopipeline.pipeline
  (:require [lambdacd.core :as core]
            [lambdacd.util :as utils]
            [ring.server.standalone :as ring-server]
            [lambdacd.ui.ui-server :as ui]
            [lambdacd.runners :as runners]
            [clojure.tools.logging :as log]

            [compojure.core :refer [routes GET POST]]
            [ring.util.response :as resp]
            [compojure.route :as route]
            [lambdacd.util.internal.temp :as temp-util])
  (:use [lambdacd.steps.control-flow]
        [todopipeline.steps])
  (:refer-clojure :exclude [alias]))

(def pipeline-def
  "the definition of the pipeline as a list of steps that are executed in order."
  `(
    ;; the first step is usually a step that waits for some event to occur, e.g.
    ;; a manual trigger or some change in the repo
    ;; the `either` control-flow element allows us to assemble a new trigger out of the two existing ones:
    ;; wait for either a change in the repository or the manual trigger.
     (alias "trigger"
       (either
         lambdacd.steps.manualtrigger/wait-for-manual-trigger
         wait-for-greeting
         wait-for-frontend-repo
         wait-for-backend-repo
         ~(if false
           always-echo-something)
         ))
     ;; you could also wait for a repository to change. to try, point the step to a repo you control,
    ;; uncomment this, run and see the magic happen (the first build will immediately run since there is no known state)
    ; wait-for-frontend-repo

    set-build-name
    ; do-stuff
     compare-screenshots
    ;; this step executes his child-steps (the arguments after the in-parallel) in parallel and waits
    ;; until all of them are done. if one of them fails, the whole step fails.
     (alias "build and publish"
       (in-parallel
        ;; these child steps do some actual work with the checked out git repo
        (with-frontend-git
          create-some-details
          client-package
          client-publish)
        (with-backend-git
          server-test
          server-package
          server-publish)
        demonstrate-ansi))
    ;; the package-scripts copy deploy-scripts and artifacts into the mockrepo directory,
    ;; execute the depoy-steps from there
     (alias "deploy"
       (in-cwd "/tmp/mockrepo"
         client-deploy-ci
         server-deploy-ci))

    ;; now we want the build to fail, just to show it's working.
    (alias "step that always fails" some-failing-step)
    some-step-that-cant-be-reached
  ))

(defn- visual-styleguide []
  (routes
    (route/resources "/styleguide/" {:root "public"})
    (GET "/" [] (resp/redirect "/styleguide/"))
    (GET "" [] (resp/redirect "/styleguide/"))
    (GET "/styleguide/" [] (resp/resource-response "visualStyleguide.html" {:root "public"}))))

(defn -main [& args]
  (let [home-dir (temp-util/create-temp-dir)
        ;; # The Configuration.
        ;; This is where you define the run-time configuration of LambdaCD. This configuration is passed on to the
        ;; individual build-steps in the `:config`-value of the context and will be used by the infrastructure and
        ;; build-steps. :home-dir is the directory where LambdaCD will store all it's internal data that should be persisted
        ;; over time, such as the last seen revisions of various git-repositories, the build history and so on.
        config {:home-dir home-dir
                :max-builds 20
                :name "some pipeline"
                :ui-config {:expand-active-default true
                            :expand-failures-default true}
                :use-new-event-bus true}
        ;; wiring everything together everything that's necessary for lambdacd to run
        pipeline (core/assemble-pipeline pipeline-def config)
        ;; the ring handler
        ring-handler (routes
                       (POST "/shutdown" [] (let [shutdown-sequence (get-in pipeline [:context :config :shutdown-sequence])]
                                              (shutdown-sequence (:context pipeline))
                                              "OK"))
                       (ui/ui-for pipeline))]
    (log/info "LambdaCD Home Directory is " home-dir)
    (runners/start-new-run-after-first-step-finished pipeline)
    (ring-server/serve ring-handler {:open-browser? false
                                     :port 8080})
    ;; visual styleguide for ui development. you don't need this in production
    (ring-server/serve (visual-styleguide) {:open-browser? false
                                     :port 8081})))
