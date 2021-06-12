(ns lambdacd.steps.shell
  "Build step to run scripts in a separate shell process. Needs `bash` to run."
  (:require [clojure.java.io :as io]
            [clojure.string :as string]
            [me.raynes.conch.low-level :as sh]
            [clojure.core.async :as async]
            [lambdacd.util.internal.temp :as temp-util]
            [lambdacd.stepsupport.output :as output])
  (:import (java.util UUID)
           (java.io IOException)
           (com.jezhumble.javasysmon JavaSysMon)))


(defn- exit-code->status [exit-code was-killed]
  (cond
    was-killed :killed
    (zero? exit-code) :success
    :default :failure))

(defn- kill [was-killed-indicator proc ctx]
  (let [pid (.pid proc)]
    (reset! was-killed-indicator true)
    (async/>!! (:result-channel ctx) [:processed-kill true])
    (.destroy proc)
    (.killProcessTree (JavaSysMon.) pid false)))

(defn- add-kill-handling [ctx proc was-killed watch-ref]
  (let [is-killed (:is-killed ctx)]
    (dosync
      (if @is-killed
        (kill was-killed proc ctx)
        (add-watch is-killed watch-ref (fn [_ _ _ new]
                                           (if new
                                             (kill was-killed proc ctx))))))))

(defn- safe-read-line [reader]
  (try
    (.readLine reader)
    (catch IOException e nil)))

(defn- read-and-print-shell-output [proc-result]
  (let [out-reader (io/reader (:out proc-result))]
    (loop []
      (let [line (safe-read-line out-reader)]
        (when line
          (println line)
          (recur))))))

(defn- execte-shell-command [cwd shell-script ctx env]
  (let [x (sh/proc "bash" "-e" shell-script
                   :dir cwd
                   :env env
                   :redirect-err true)
        proc (:process x)
        was-killed (atom false)
        kill-switch (:is-killed ctx)
        watch-ref (UUID/randomUUID)
        _ (add-kill-handling ctx proc was-killed watch-ref)
        out (read-and-print-shell-output x)
        exit-code (sh/exit-code x)
        status (exit-code->status exit-code @was-killed)]
    (remove-watch kill-switch watch-ref)
    {:exit exit-code :status status :out out}))

(defn bash
  "step that executes commands in a bash. arguments are the working-directory and at least one command to execute
  returns stdout and stderr as :out value, the exit code as :exit and succeeds if exit-code was 0"
  [ctx cwd & optional-env-and-commands]
  (let [temp-file (temp-util/create-temp-file)
        env-or-first-command (first optional-env-and-commands)
        env (if (map? env-or-first-command) env-or-first-command {})
        commands (if (map? env-or-first-command) (rest optional-env-and-commands) optional-env-and-commands)
        command-lines (string/join "\n" commands)]
    (spit temp-file command-lines)
    (temp-util/with-temp temp-file
                         (output/capture-output ctx
                           (execte-shell-command cwd temp-file ctx env)))))
