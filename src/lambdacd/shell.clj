(ns lambdacd.shell
  (:require [clojure.java.io :as io]
            [clojure.string :as string]
            [me.raynes.conch.low-level :as sh]))


(defn- exit-code->status [exit-code]
  (if (zero? exit-code)
    :success
    :failure))

(defn- mysh [cwd cmd ctx]
  (let [x (sh/proc "bash" "-c" cmd :dir cwd)
        out-reader (io/reader (:out x))
        out (loop [acc ""]
                (let [v (.readLine out-reader)
                      new-acc (str acc v "\n")]
                  (if v
                    (do
                      (lambdacd.execution/send-output ctx :out new-acc)
                      (recur new-acc))
                    acc)))
          exit-code (sh/exit-code x)
          status (exit-code->status exit-code)]
    {:exit exit-code :status status :out out}))

(defn bash
  "step that executes commands in a bash. arguments are the working-directory and at least one command to execute
  returns stdout and stderr as :out value, the exit code as :exit and succeeds if exit-code was 0"
  [ctx cwd & commands]
  (let [combined-command (str "bash -c '" (string/join " && " commands) "' 2>&1") ;; very hacky but it does the job of redirecting stderr to stdout
        shell-result (mysh cwd combined-command ctx)]
    shell-result))