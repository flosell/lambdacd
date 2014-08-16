(ns todopipeline.steps
  (:require [lambdaci.shell :as shell]
            [lambdaci.dsl :as dsl]))


;; pipeline steps


(defn in-cwd [specified-working-directory & steps]
  (fn [_]
    (dsl/execute-steps steps {:cwd specified-working-directory })))



;; TODO: this cannot handle error cases in the first two scripts
(defn client-package [{cwd :cwd}]
  (shell/bash cwd
    "bower install"
    "./package.sh"
    "./publish.sh"))

(defn server-test [{cwd :cwd}]
  (shell/bash cwd
    "lein test"))

(defn server-package [{cwd :cwd}]
  (shell/bash cwd
    "lein uberjar"
    "./publish.sh"))

(defn server-deploy-ci [{cwd :cwd}]
  (shell/bash cwd "./deploy.sh backend_ci /tmp/mockrepo/server-snapshot.tar.gz"))

(defn client-deploy-ci [{cwd :cwd}]
  (shell/bash cwd "./deploy.sh frontend_ci /tmp/mockrepo/client-snapshot.tar.gz"))


;; ----------------------------------------

(defn doCompile [a]
  (println "start compiling")
  (Thread/sleep 1000)
  (println "compiling done")
  {
   :artifacts ["build/*.jar"]})

(defn check [{artifacts :artifacts}]
  (println "Running unit tests")
  (Thread/sleep 2000)
  (println "Testresult: 10/10 succeeded")
  { :results
    { :testng
      { :successful 10
        :failed 0
        :running 10}}})

(defn jscheck [{artifacts :artifacts}]
  (println "Running Jasmine tests")
  (Thread/sleep 1000)
  (println "Testresult: 2 succeeded")
  { :results
    { :jasmine
      { :successful 10
        :failed 0}}})

(defn rsatobs [{artifacts :artifacts}]
  (println "Running Selenium Tests")
  (Thread/sleep 10000)
  (println "Testresult: 1 succeeded")
  { :results
    { :testng
      { :successful 1
        :failed 0
        :running 1}}})

(defn publishrpm [{artifacts :artifacts}]
  (println "Publishing artifacts")
;  (println (:out (sh "bash" "-c" "echo foo")))
  (Thread/sleep 1000)
  (println "Published artifact 1.75.19876")
  { :properties { :nexus-version "1.75.19876"}})

(defn deploy-ci [{{nexus-version :nexus-version} :properties}]
  (println (str "Deploying Nexus Version " nexus-version))
  (println "deployed"))
