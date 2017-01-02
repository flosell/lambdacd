(ns lambdacd.steps.git
  "build-steps that let you work with git repositories"
  (:import (java.io File))
  (:require [lambdacd.execution.core :as execution]
            [clojure.tools.logging :as log]
            [lambdacd.steps.shell :as shell]
            [clojure.core.async :as async]
            [lambdacd.presentation.pipeline-state :as pipeline-state]
            [lambdacd.steps.support :as support]
            [clojure.string :as s]
            [clojure.java.io :as io]
            [lambdacd.util :as utils]
            [lambdacd.util.internal.bash :as bash-util]
            [lambdacd.steps.status :as status]
            [lambdacd.util.internal.temp :as temp-util]))

(defn- current-revision [repo-uri branch]
  (log/debug (str "Polling branch " branch " on " repo-uri))
  (let [shell-output (bash-util/bash "/"
                       "set -o pipefail"
                       (str "git ls-remote --heads " repo-uri " " branch " | cut -f 1"))
        revision (.trim (:out shell-output))]
    (assoc shell-output :revision revision)))

(defn- revision-changed-from [last-seen-revision repo-uri branch]
  (let [revision-output (current-revision repo-uri branch)
        exit-code (:exit revision-output)
        new-revision-output (:revision revision-output)]
    (if (not= 0 exit-code)
      {:status :failure :out (:out revision-output)}
      (do
        (log/debug "waiting for new revision. current revision" revision-output "last seen" last-seen-revision)
        (when (not= last-seen-revision new-revision-output)
          {:status :success :revision new-revision-output :old-revision last-seen-revision})))))

(defn- wait-for-revision-changed-from [last-seen-revision repo-uri branch ctx ms-between-polls]
  (let [initial-output (str "Last seen revision: " (or last-seen-revision "None") ". Waiting for new commit...")]
    (async/>!! (:result-channel ctx) [:status :waiting])
    (async/>!! (:result-channel ctx) [:out initial-output])
    (loop [result (revision-changed-from last-seen-revision repo-uri branch)]
      (support/if-not-killed ctx
        (if (= :success (:status result))
          (do
            (async/>!! (:result-channel ctx) [:out (str initial-output "\nFound new commit: " (:revision result) ".")])
            result)
          (do (Thread/sleep ms-between-polls)
              (recur (revision-changed-from last-seen-revision repo-uri branch))))))))

(defn- last-seen-revision-for-this-step [ctx repo-uri branch]
  (let [last-step-result (pipeline-state/most-recent-step-result-with :_git-last-seen-revision ctx)
        last-seen-revision-in-history (:_git-last-seen-revision last-step-result)]
    (if-not (nil? last-seen-revision-in-history)
      last-seen-revision-in-history
      (:revision (current-revision repo-uri branch)))))

(defn- persist-last-seen-revision [wait-for-result last-seen-revision ctx]
  (let [current-revision (:revision wait-for-result)
        revision-to-persist (or current-revision last-seen-revision)]
    (async/>!! (:result-channel ctx) [:_git-last-seen-revision revision-to-persist]) ; by sending it through the result-channel, we can be pretty sure users don't overwrite it
    (assoc wait-for-result :_git-last-seen-revision revision-to-persist)))

(defn wait-for-git
  "step that waits for the head of a branch to change"
  [ctx repo-uri branch & {:keys [ms-between-polls]
                          :or   {ms-between-polls (* 10 1000)}}]
  (let [last-seen-revision (last-seen-revision-for-this-step ctx repo-uri branch)
        wait-for-result (wait-for-revision-changed-from last-seen-revision repo-uri branch ctx ms-between-polls)]
    (persist-last-seen-revision wait-for-result last-seen-revision ctx)))

(defn- home-dir [ctx]
  (:home-dir (:config ctx)))

(defn- checkout [ctx repo-uri revision]
  (let [home-dir (home-dir ctx)
        base-dir (temp-util/create-temp-dir home-dir)
        sh-result (shell/bash ctx base-dir
                              (str "echo \"Cloning " revision " of " repo-uri "\"")
                              (str "git clone " repo-uri )
                              "cd $(ls)"
                              (str "git checkout " revision))
        content-of-git-tmp-dir (first (.list (File. base-dir)))
        checkout-folder-name (str base-dir "/" content-of-git-tmp-dir)]
    (assoc sh-result :cwd checkout-folder-name)))

(defn- last-step-id-of [step-ids]
  (last (sort-by first step-ids)))

(defn checkout-and-execute [repo-uri revision args ctx steps]
  (let [checkout-result (checkout ctx repo-uri revision)
        repo-location (:cwd checkout-result)
        checkout-exit-code (:exit checkout-result)]
    (temp-util/with-temp repo-location
      (if (zero? checkout-exit-code)
        (let [execute-steps-result (execution/execute-steps steps (assoc args :cwd repo-location) ctx
                                                            :unify-results-fn (support/unify-only-status status/successful-when-all-successful)
                                                            :is-killed (:is-killed ctx))
              result-with-checkout-output (assoc execute-steps-result :out (:out checkout-result))
              step-ids-and-outputs (:outputs execute-steps-result)
              step-ids (keys step-ids-and-outputs)
              outputs (vals step-ids-and-outputs)
              last-step-id (last-step-id-of step-ids)
              output-of-last-step (get-in execute-steps-result [:outputs last-step-id])
              globals (support/merge-globals outputs)]
          (merge result-with-checkout-output output-of-last-step {:global globals}))
        {:status :failure
         :out (:out checkout-result)
         :exit (:exit checkout-result)}))))

(defn with-git
  "creates a container-step that checks out a given revision from a repository.
   the revision number is passed on as the :revision value in the arguments-map"
  [repo-uri steps]
  (fn [args ctx]
    (checkout-and-execute repo-uri (:revision args) args ctx steps)))

(defn with-git-branch
  "creates a container-step that checks out the latest revision from a repository with the
  given branch."
  [repo-uri repo-branch steps]
  (fn [args ctx]
    (checkout-and-execute repo-uri repo-branch args ctx steps)))

(defn- parse-log-lines [l]
  (let [[hash & msg-parts] (s/split l #" ")
        msg (s/join " " msg-parts)]
    [hash {:msg msg}]))

(defn- parse-log [git-oneline-log]
  (let [lines (s/split-lines git-oneline-log)
        parsed-lines (into {} (map parse-log-lines lines))]
    parsed-lines))

(defn with-commit-details
  "given :revision and :old-revision (as wait-for-git provides them), enriches
   the data with details about the commits between these revisions"
  [ctx repo args]
  (let [old-revision (:old-revision args)
        new-revision (:revision args)
        dir (temp-util/create-temp-dir (home-dir ctx))
        _ (shell/bash ctx dir (str "git clone --depth 100 " repo " repo"))
        log-result (shell/bash ctx
                               (io/file dir "repo")
                               (str "git log --pretty=oneline " old-revision "..." new-revision))
        log-output (:out log-result)
        commits (parse-log log-output)
        original-out (:out args)
        new-out (str "\n\nChanges between commits:\n\n" original-out log-output)]
    (assoc args :commits commits :out new-out)))

(defn wait-with-details [ctx repo branch & opts]
  (let [wait-result (apply wait-for-git ctx repo branch opts)]
    (if (= :success (:status wait-result))
      (with-commit-details ctx repo wait-result)
      wait-result)))
