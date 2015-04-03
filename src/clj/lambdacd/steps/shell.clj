(ns lambdacd.steps.shell
  (:require [lambdacd.util :as utils]
            [clojure.java.io :as io]
            [clojure.string :as string]
            [me.raynes.conch.low-level :as sh]
            [clojure.core.async :as async]
            [lambdacd.internal.execution :as execution])
  (:import (java.util UUID)))


(defn- exit-code->status [exit-code was-killed]
  (cond
    was-killed :killed
    (zero? exit-code) :success
    :default :failure))

(defn- mysh [cwd cmd  ctx]
  (let [result-ch (:result-channel ctx)
        x (sh/proc "bash" "-c" cmd :dir cwd)
        proc (:process x)
        out-reader (io/reader (:out x))
        watch-ref (UUID/randomUUID)
        was-killed (atom false)
        kill-switch (:is-killed ctx)
        _ (add-watch kill-switch watch-ref (fn [_ _ _ new]
                                                    (if new
                                                      (do
                                                        (reset! was-killed true)
                                                        (.destroyForcibly proc)))))
        out (loop [acc ""]
              (let [v (.readLine out-reader)
                      new-acc (str acc v "\n")]
                  (if v
                    (do
                      (async/>!! result-ch [:out new-acc] )
                      (recur new-acc))
                    acc)))
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