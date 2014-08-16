(ns lambdaci.shell
  (:require [clojure.java.shell :as jsh]
            [clojure.string :as string]))

(defn mysh [cwd cmd]
  (jsh/sh "bash" "-c" cmd :dir cwd))

(defn bash [cwd & commands]
  (let [combined-command (string/join " && " commands)]
  (mysh cwd combined-command)))


