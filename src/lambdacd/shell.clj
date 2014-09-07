(ns lambdacd.shell
  (:require [clojure.java.shell :as jsh]
            [clojure.string :as string]))

(defn- mysh [cwd cmd]
  (jsh/sh "bash" "-c" cmd  :dir cwd))

(defn- exit-code->status [exit-code]
  (if (= exit-code 0)
    :success
    :failure))

(defn bash
  "step that executes commands in a bash. arguments are the working-directory and at least one command to execute
  returns stdout and stderr as :out value, the exit code as :exit and succeeds if exit-code was 0"
  [cwd & commands]
  (let [combined-command (str "bash -c '" (string/join " && " commands) "' 2>&1") ;; very hacky but it does the job of redirecting stderr to stdout
        result (mysh cwd combined-command)]
    (assoc result :status (exit-code->status (:exit result)))))
