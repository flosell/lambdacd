(ns lambdaci.somepipeline.pipeline
  (:require [clojure.tools.nrepl :as repl]
   :use [lambdaci.dsl]
        [lambdaci.git]
        [lambdaci.somepipeline.steps]))

(def deploymentPipeline
  (defbuild
    (git "git@github.com:flosell/testrepo")
    (execute
     doCompile
     (inParallel
       check
       jscheck
       rsatobs)
     publishrpm
     deploy-ci)))
