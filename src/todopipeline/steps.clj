(ns todopipeline.steps
  (:require [lambdacd.shell :as shell]
            [lambdacd.execution :as execution]
            [lambdacd.git :as git]
            [lambdacd.manualtrigger :as manualtrigger]))

(def backend-repo "git@github.com:flosell/todo-backend-compojure.git")
(def frontend-repo "git@github.com:flosell/todo-backend-client.git")

(defn wait-for-frontend-repo [& _]
  (git/wait-for-git frontend-repo "master"))


(defn ^{:display-type :container} with-frontend-git [& steps]
  (git/with-git frontend-repo steps))

(defn ^{:display-type :container} with-backend-git [& steps]
  (git/with-git backend-repo steps))


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
  (shell/bash cwd "./deploy-server.sh backend_ci /tmp/mockrepo/server-snapshot.tar.gz"))

(defn client-deploy-ci [{cwd :cwd} & _]
  (shell/bash cwd "./deploy-frontend.sh frontend_ci /tmp/mockrepo/client-snapshot.tar.gz"))

(defn some-failing-step [& _]
  (shell/bash "/" "echo 'i am going to fail now...'" "exit 1"))

(defn some-step-that-cant-be-reached [& _]
  {:status :success})
