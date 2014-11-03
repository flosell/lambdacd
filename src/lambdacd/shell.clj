(ns lambdacd.shell
  (:require [lambdacd.util :as utils]
            [clojure.java.io :as io]
            [clojure.string :as string]
            [me.raynes.conch.low-level :as sh]
            [clojure.core.async :as async]))


(defn- exit-code->status [exit-code]
  (if (zero? exit-code)
    :success
    :failure))

(defn- mysh [cwd cmd]
  (let [x (sh/proc "bash" "-c" cmd :dir cwd)
        out (io/reader (:out x))
        result-ch (async/chan 10)]
    (async/go
      (loop [acc ""]
        (let [v (.readLine out)
              new-acc (str acc v "\n")]
          (if v
            (do
              (async/>! result-ch [:out new-acc] )
              (recur new-acc)))))
      (let [exit-code (sh/exit-code x)
            status (exit-code->status exit-code)]
        (async/>! result-ch [:exit exit-code])
        (async/>! result-ch [:status status])
        (async/close! result-ch)))
     result-ch))

(defn bash
  "step that executes commands in a bash. arguments are the working-directory and at least one command to execute
  returns stdout and stderr as :out value, the exit code as :exit and succeeds if exit-code was 0"
  [cwd & commands]
  (let [combined-command (str "bash -c '" (string/join " && " commands) "' 2>&1") ;; very hacky but it does the job of redirecting stderr to stdout
        shell-result (mysh cwd combined-command)]
    shell-result))