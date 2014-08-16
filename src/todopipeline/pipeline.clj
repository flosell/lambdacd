(ns todopipeline.pipeline
  (:require [clojure.tools.nrepl :as repl]
   :use [lambdaci.dsl]
        [lambdaci.git]
        [todopipeline.steps]))

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
