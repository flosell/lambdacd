(ns lambdacd.util
  (:import (java.nio.file Files)
           (java.nio.file.attribute FileAttribute)
           (org.joda.time DateTime))
  (:require [clojure.string :as string]
            [clojure.java.shell :as jsh]
            [clojure.tools.logging :as log]
            [clojure.data.json :as json]
            [clojure.java.io :as io]
            [cheshire.core :as ch]
            [cheshire.generate :as chg]
            [clj-time.format :as f]))

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

(defn parse-int [int-str]
  (Integer/parseInt int-str))

(defn contains-value? [v coll]
  (some #(= % v) coll))

(def iso-formatter (f/formatters :date-time))

(chg/add-encoder DateTime (fn [v jsonGenerator] (.writeString jsonGenerator (f/unparse iso-formatter v))))

(defn to-json [v]
  (ch/generate-string v))

(defn json [data]
  {:headers { "Content-Type" "application/json"}
   :body (to-json data)
   :status 200 })

(defn ok []
  {:headers { "Content-Type" "application/json"}
   :body "{}"
   :status 200 })

(defn fill [coll length filler]
  (let [missing (- length (count coll))]
  (concat coll (replicate missing filler))))
