(ns lambdacd.util.internal.bash
  (:require [clojure.tools.logging :as log]
            [clojure.java.shell :as jsh]
            [clojure.string :as string]))

(defn bash
  [cwd & commands]
  (let [combined-command (str "bash -c '" (string/join " && " commands) "' 2>&1") ;; very hacky but it does the job of redirecting stderr to stdout
        result (jsh/sh "bash" "-c" combined-command :dir cwd)]
    (log/debug (str "executed " combined-command " in " cwd " with result " result))
    result))

