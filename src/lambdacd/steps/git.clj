(ns lambdacd.steps.git
  "build-steps that let you work with git repositories"
  (:import (java.io File))
  (:require [lambdacd.steps.shell :as sh]
            [lambdacd.execution :as execution]
            [lambdacd.util :as util]
            [clojure.data.json :as json]
            [clojure.tools.logging :as log]
            [lambdacd.steps.shell :as shell]
            [clojure.core.async :as async]
            [lambdacd.pipeline-state :as pipeline-state]))

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
          {:status :success :revision new-revision-output}
          nil)))))

(defn- wait-for-revision-changed-from [last-seen-revision repo-uri branch ctx]
  (let [initial-output (str "Last seen revision: " (or last-seen-revision "None") ". Waiting for new commit...")]
    (async/>!! (:result-channel ctx) [:status :waiting])
    (async/>!! (:result-channel ctx) [:out initial-output])
    (loop [result (revision-changed-from last-seen-revision repo-uri branch)]
      (execution/if-not-killed ctx
        (if (= :success (:status result))
          (do
            (async/>!! (:result-channel ctx) [:out (str initial-output "\nFound new commit: " (:revision result) ".")])
            result)
          (do (Thread/sleep 1000)
              (recur (revision-changed-from last-seen-revision repo-uri branch))))))))

(defn- last-seen-revision-for-this-step [ctx]
  (let [last-step-result (pipeline-state/most-recent-step-result-with :_git-last-seen-revision ctx)
        last-seen-revision (:_git-last-seen-revision last-step-result)]
    last-seen-revision))

(defn- persist-last-seen-revision [wait-for-result ctx]
  (let [current-revision (:revision wait-for-result)]
    (async/>!! (:result-channel ctx) [:_git-last-seen-revision current-revision]) ; by sending it through the result-channel, we can be pretty sure users don't overwrite it
    wait-for-result))

(defn wait-for-git
  "step that waits for the head of a branch to change"
  [ctx repo-uri branch]
  (let [last-seen-revision (last-seen-revision-for-this-step ctx)
        wait-for-result (wait-for-revision-changed-from last-seen-revision repo-uri branch ctx)]
    (persist-last-seen-revision wait-for-result ctx)))

(defn- checkout [ctx repo-uri revision]
  (let [home-dir (:home-dir (:config ctx))
        base-dir (util/create-temp-dir home-dir)
        sh-result (shell/bash ctx base-dir
                              (str "echo \"Cloning " revision " of " repo-uri "\"")
                              (str "git clone " repo-uri )
                              "cd $(ls)"
                              (str "git checkout " revision))
        content-of-git-tmp-dir (first (.list (File. base-dir)))
        checkout-folder-name (str base-dir "/" content-of-git-tmp-dir)]
    (assoc sh-result :cwd checkout-folder-name)))

(defn- last-step-id-of [step-ids]
  (last (sort-by #(first %) step-ids)))

(defn checkout-and-execute [repo-uri revision args ctx steps]
  (let [checkout-result (checkout ctx repo-uri revision)
        repo-location (:cwd checkout-result)
        checkout-exit-code (:exit checkout-result)]
    (if (= 0 checkout-exit-code)
      (let [execute-steps-result (execution/execute-steps steps (assoc args :cwd repo-location) (execution/new-base-context-for ctx))
            result-with-checkout-output (assoc execute-steps-result :out (:out checkout-result))
            step-ids (keys (:outputs execute-steps-result))
            last-step-id (last-step-id-of step-ids)
            output-of-last-step (get-in execute-steps-result [:outputs last-step-id])]
        (assoc (merge result-with-checkout-output output-of-last-step) :cwd repo-location))
      {:status :failure
       :out (:out checkout-result)
       :exit (:exit checkout-result)
       :cwd repo-location})))

(defn with-git
  "creates a container-step that checks out a given revision from a repository.
   the revision number is passed on as the :revision value in the arguments-map"
  [repo-uri steps]
  (fn [args ctx]
    (checkout-and-execute repo-uri (:revision args) args ctx steps)))
