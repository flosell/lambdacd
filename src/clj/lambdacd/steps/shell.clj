(ns lambdacd.steps.shell
  (:require [lambdacd.steps.support :as support]
            [clojure.java.io :as io]
            [clojure.string :as string]
            [me.raynes.conch.low-level :as sh]
            [clojure.core.async :as async]
            [lambdacd.internal.execution :as execution])
  (:import (java.util UUID)
           (java.io IOException)))


(defn- exit-code->status [exit-code was-killed]
  (cond
    was-killed :killed
    (zero? exit-code) :success
    :default :failure))

(defn kill [was-killed-indicator proc]
  (reset! was-killed-indicator true)
  (.destroy proc))

(defn- add-kill-handling [ctx proc was-killed watch-ref]
  (let [is-killed (:is-killed ctx)]
    (dosync
      (if @is-killed
        (kill was-killed proc)
        (add-watch is-killed watch-ref (fn [_ _ _ new]
                                           (if new
                                             (kill was-killed proc))))))))

(defn- safe-read-line [reader]
  (try
    (.readLine reader)
    (catch IOException e nil)))

(defn- mysh [cwd cmd  ctx]
  (let [result-ch (:result-channel ctx)
        x (sh/proc "bash" "-c" cmd :dir cwd)
        proc (:process x)
        out-reader (io/reader (:out x))
        was-killed (atom false)
        kill-switch (:is-killed ctx)
        watch-ref (UUID/randomUUID)
        _ (add-kill-handling ctx proc was-killed watch-ref)
        printer (support/new-printer)
        out (loop []
              (let [v (safe-read-line out-reader)]
                  (if v
                    (do
                      (support/print-to-output ctx printer v)
                      (recur))
                    (support/printed-output printer))))
          exit-code (sh/exit-code x)
          status (exit-code->status exit-code @was-killed)]
    (async/close! result-ch)
    (remove-watch kill-switch watch-ref)
    {:exit exit-code :status status :out out}))

(defn bash
  "step that executes commands in a bash. arguments are the working-directory and at least one command to execute
  returns stdout and stderr as :out value, the exit code as :exit and succeeds if exit-code was 0"
  [ctx cwd & commands]
  (let [combined-command (str "bash -c '" (string/join " && " commands) "' 2>&1") ;; very hacky but it does the job of redirecting stderr to stdout
        shell-result (mysh cwd combined-command ctx)]
    shell-result))