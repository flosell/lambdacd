(ns todopipeline.steps
  (:require [lambdaci.shell :as shell]
            [lambdaci.dsl :as dsl]
            [lambdaci.git :as git]
            [lambdaci.manualtrigger :as manualtrigger]))

(defn wait-for-backend-repo [& _]
  (git/wait-for-git "file:///Users/fsellmay/Code/pipeline-as-code/todo-backend-client" "master"))


(defn ^{:display-type :container} with-frontend-git [& steps]
  (git/with-git "file:///Users/fsellmay/Code/pipeline-as-code/todo-backend-client" steps))

(defn ^{:display-type :container} with-backend-git [& steps]
  (git/with-git "file:///Users/fsellmay/Code/pipeline-as-code/todo-backend-compojure" steps))


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
  (shell/bash "/" "echo 'i am going to fail now...'" "exit 1"))

(defn some-step-that-cant-be-reached [& _]
  {:status :success})
