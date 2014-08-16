(ns todopipeline.pipeline
   (:use [lambdaci.dsl]
         [lambdaci.git]
         [todopipeline.steps]))



(def pipeline
  `(
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
  ))
