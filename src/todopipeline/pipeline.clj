(ns todopipeline.pipeline
  (:require [clojure.tools.nrepl :as repl]
   :use [lambdaci.dsl]
        [lambdaci.git]
        [todopipeline.steps]))

;;(def deploymentPipeline
;;  (defbuild
;;    (git "git@github.com:flosell/testrepo")
;;    (execute
;;     doCompile
;;     (inParallel
;;       check
;;       jscheck
;       rsatobs)
;;     publishrpm
;;     deploy-ci)))


(def client-pipeline
  (defbuild
    (in-cwd "/Users/fsellmay/Code/pipeline-as-code/todo-backend-client" ;; I can't checkout yet so this will to to set up a working dir
      client-package)
    (in-cwd "/Users/fsellmay/Code/pipeline-as-code/todo-backend-compojure"
      server-test
      server-package)
    (in-cwd "/Users/fsellmay/Code/pipeline-as-code/todo-backend-client" ;; I can't checkout yet so this will to to set up a working dir
      client-deploy-ci)
    (in-cwd "/Users/fsellmay/Code/pipeline-as-code/todo-backend-compojure"
      server-deploy-ci)
  ))
