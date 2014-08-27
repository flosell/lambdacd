(ns todopipeline.pipeline
   (:use [lambdaci.dsl]
         [todopipeline.steps]))



(def pipeline
  `(
    wait-for-backend-repo
    (in-parallel
      (in-cwd "/Users/fsellmay/Code/pipeline-as-code/todo-backend-client" ;; I can't checkout yet so this will to to set up a working dir
        client-package)
      (in-cwd "/Users/fsellmay/Code/pipeline-as-code/todo-backend-compojure"
        server-test
        server-package))
    (in-cwd "/Users/fsellmay/Code/pipeline-as-code/todo-backend-client"
      client-deploy-ci)
    (in-cwd "/Users/fsellmay/Code/pipeline-as-code/todo-backend-compojure"
      server-deploy-ci)
    some-failing-step
    some-step-that-cant-be-reached
  ))
