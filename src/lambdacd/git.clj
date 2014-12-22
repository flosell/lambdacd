(ns lambdacd.git
  "build-steps that let you work with git repositories"
  (:import (java.io File))
  (:require [lambdacd.shell :as sh]
            [lambdacd.execution :as execution]
            [lambdacd.util :as util]
            [clojure.data.json :as json]
            [clojure.tools.logging :as log]))

(defn- current-revision [repo-uri branch]
  (util/bash "/"
             "set -o pipefail"
             (str "git ls-remote --heads " repo-uri " " branch " | cut -f 1")))


(defn- revision-changed-from [last-seen-revision repo-uri branch]
  (let [revision-output (current-revision repo-uri branch)
        exit-code (:exit revision-output)
        new-revision-output (.trim (:out revision-output))]
    (if (not= 0 exit-code)
      {:status :failure :out (:out revision-output)}
      (do
        (log/debug "waiting for new revision. current revision" revision-output "last seen" last-seen-revision)
        (if (not= last-seen-revision new-revision-output)
          {:status :success :current-revision new-revision-output}
          nil)))))

(defn- wait-for-revision-changed-from [last-seen-revision repo-uri branch]
  (loop [result (revision-changed-from last-seen-revision repo-uri branch)]
    (if (nil? result)
      (do (Thread/sleep 1000)
          (recur (revision-changed-from last-seen-revision repo-uri branch)))
      result)))

(defn- exists? [file]
  (.exists (File. file)))

(defn- git-state-file-from [ctx]
  (str (:home-dir (:config ctx)) "/git-state.json"))

(defn- read-git-state [ctx]
  (let [git-state-file (git-state-file-from ctx)]
    (if (exists? git-state-file)
      (json/read-str (slurp git-state-file))
      {})))


(defn- last-seen-revision-for [ctx repo-uri branch]
  (let [git-state-data (read-git-state ctx)
        last-seen-revision (get (get (get git-state-data "last-seen") repo-uri) branch)]
    last-seen-revision))

(defn- write-git-state [ctx new-git-state]
  (let [out-file (git-state-file-from ctx)]
    (util/write-as-json out-file new-git-state)))

(defn- persist-last-seen-revision [ctx repo-uri branch last-seen-revision]
  (let [current-git-state (read-git-state ctx)
        new-git-state (merge current-git-state { "last-seen" { repo-uri { branch last-seen-revision }}} )]
    (write-git-state ctx new-git-state)))

(defn wait-for-git
  "step that waits for the head of a branch to change"
  [ctx repo-uri branch]
  (if (nil? (:home-dir (:config ctx)))
    {:status :failure :out "No :home-dir configured"}
    (let [last-seen-revision (last-seen-revision-for ctx repo-uri branch)
          wait-for-result (wait-for-revision-changed-from last-seen-revision repo-uri branch)
          current-revision (:current-revision wait-for-result)]
      (persist-last-seen-revision ctx repo-uri branch current-revision)
      wait-for-result)))

(defn- checkout [repo-uri revision]
  (let [cwd (util/create-temp-dir)
        sh-result (util/bash cwd
                             (str "git clone " repo-uri )
                             "cd $(ls)"
                             (str "git checkout " revision))
        content (first (.list (File. cwd)))]
    (assoc sh-result :cwd (str cwd "/" content))))


(defn with-git
  "creates a container-step that checks out a given revision from a repository.
   the revision number is passed on as the :revision value in the arguments-map"
  [repo-uri steps]
  (fn [args ctx]
    (let [checkout-result (checkout repo-uri (:revision args))  ;; TODO: wouldn't it be better to pass in the revision?
          repo-location (:cwd checkout-result)
          checkout-exit-code (:exit checkout-result)]
      (if (= 0 checkout-exit-code)
        (let [execute-steps-result (execution/execute-steps steps (assoc args :cwd repo-location) (execution/new-base-context-for ctx))]
          (assoc execute-steps-result :out (:out checkout-result)))
        {:status :failure :out (:out checkout-result) :exit (:exit checkout-result)}))))

