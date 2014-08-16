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
    (cwd "/Users/fsellmay/Code/pipeline-as-code/todo-backend-client") ;; I can't checkout yet so this will to to set up a working dir
    client-package
    ))
