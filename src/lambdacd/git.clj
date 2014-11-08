(ns lambdacd.git
  "build-steps that let you work with git repositories"
  (:import (java.io File))
  (:require [lambdacd.shell :as sh]
            [lambdacd.execution :as execution]
            [lambdacd.util :as util]
            [clojure.data.json :as json]
            [clojure.tools.logging :as log]))

(defn- current-revision [repo-uri branch]
  (.trim (:out (util/bash "/" (str "git ls-remote --heads " repo-uri " " branch " | cut -f 1")))))


(defn- revision-changed-from [last-seen-revision repo-uri branch]
  (fn []
    (let [revision-now (current-revision repo-uri branch)]
      (log/debug "waiting for new revision. current revision" revision-now "last seen" last-seen-revision)
      (not= last-seen-revision revision-now))))

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
    (spit out-file (json/write-str new-git-state))))

(defn- persist-last-seen-revision [ctx repo-uri branch last-seen-revision]
  (let [current-git-state (read-git-state ctx)
        new-git-state (merge current-git-state { "last-seen" { repo-uri { branch last-seen-revision }}} )]
    (write-git-state ctx new-git-state))
  )

(defn wait-for-git
  "step that waits for the head of a branch to change"
  [ctx repo-uri branch]
  (if (nil? (:home-dir (:config ctx)))
    {:status :failure :out "No :home-dir configured"}
    (let [last-seen-revision (last-seen-revision-for ctx repo-uri branch)
          wait-for-result (execution/wait-for (revision-changed-from last-seen-revision repo-uri branch))
          current-revision (current-revision repo-uri branch)]
      (persist-last-seen-revision ctx repo-uri branch current-revision)
      {:status :success :current-revision current-revision})))

(defn- checkout [repo-uri revision]
  (let [cwd (util/create-temp-dir)]
    (util/bash cwd (str "git clone " repo-uri " .") (str "git checkout " revision))
    cwd))


(defn with-git
  "creates a container-step that checks out a given revision from a repository.
   the revision number is passed on as the :revision value in the arguments-map"
  [repo-uri steps]
  (fn [args ctx]
    (let [repo-location (checkout repo-uri (:revision args))] ;; TODO: wouldn't it be better to pass in the revision?
      (execution/execute-steps steps (assoc args :cwd repo-location) (execution/new-base-context-for ctx)))))

