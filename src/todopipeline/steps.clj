(ns todopipeline.steps
  (:require [lambdaci.shell :as shell]
            [lambdaci.dsl :as dsl]))

(defn client-package [{cwd :cwd} & _]
  (shell/bash cwd
    "bower install"
    "./package.sh"
    "./publish.sh"))

(defn server-test [{cwd :cwd} & _]
  (shell/bash cwd
    "lein test"))

(defn server-package [{cwd :cwd} & _]
  (shell/bash cwd
    "lein uberjar"
    "./publish.sh"))

(defn server-deploy-ci [{cwd :cwd} & _]
  (shell/bash cwd "./deploy.sh backend_ci /tmp/mockrepo/server-snapshot.tar.gz"))

(defn client-deploy-ci [{cwd :cwd} & _]
  (shell/bash cwd "./deploy.sh frontend_ci /tmp/mockrepo/client-snapshot.tar.gz"))

(defn some-failing-step [& _]
  {:status :failure})

(defn some-step-that-cant-be-reached [& _]
  {:status :success})
