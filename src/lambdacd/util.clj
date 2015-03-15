(ns lambdacd.util
  (:import (java.nio.file Files)
           (java.nio.file.attribute FileAttribute)
           (clojure.lang Named))
  (:require [clojure.string :as string]
            [clojure.java.shell :as jsh]
            [clojure.tools.logging :as log]
            [clojure.data.json :as json]
            [clojure.java.io :as io]))

(defn range-from [from len] (range (inc from) (+ (inc from) len)))

(defn create-temp-dir
  ([]
    (str (Files/createTempDirectory "foo" (into-array FileAttribute []))))
  ([parent]
    (str (Files/createTempDirectory (.toPath (io/file parent)) "foo" (into-array FileAttribute [])))))

(defn write-as-json [file data]
  (spit file (json/write-str data)))

(defn bash
  [cwd & commands]
  (let [combined-command (str "bash -c '" (string/join " && " commands) "' 2>&1") ;; very hacky but it does the job of redirecting stderr to stdout
        result (jsh/sh "bash" "-c" combined-command  :dir cwd)]
    (log/debug (str "executed " combined-command " in " cwd " with result " result))
    result))


(defn map-if [pred f coll]
  "applies f to all elements in coll where pred is true"
  (map #(if (pred %)
         (f %)
         %) coll))


(defn contains-value? [v coll]
  (some #(= % v) coll))

; TODO: this is only necessary because we were lazy when we initially built the ui and just dump the internal datastructure into json
; get rid of this once we build a proper api for the ui
(defn- handle-unorthodox-keys
  [x]
  (cond (instance? Named x) (name x)
        (nil? x) (throw (Exception. "JSON object properties may not be nil"))
        (seq? x) (str (seq (vec x)))
        :else (str x)))

(defn json [data]
  {:headers { "Content-Type" "application/json"}
   :body (json/write-str data :key-fn handle-unorthodox-keys)
   :status 200 })

(defn ok []
  {:headers { "Content-Type" "application/json"}
   :body "{}"
   :status 200 })