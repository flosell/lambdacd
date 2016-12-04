(ns lambdacd.util
  (:import (java.nio.file Files LinkOption)
           (java.nio.file.attribute FileAttribute)
           (org.joda.time DateTime))
  (:require [clojure.string :as string]
            [clojure.java.shell :as jsh]
            [clojure.tools.logging :as log]
            [clojure.data.json :as json]
            [clojure.java.io :as io]
            [cheshire.core :as ch]
            [cheshire.generate :as chg]
            [me.raynes.fs :as fs]
            [clj-time.format :as f]
            [clojure.core.async :as async]))

(defn range-from [from len] (range (inc from) (+ (inc from) len)))

(defn no-file-attributes []
  (into-array FileAttribute []))


(def temp-prefix "lambdacd")

(defn create-temp-dir
  ([]
    (str (Files/createTempDirectory temp-prefix (no-file-attributes))))
  ([parent]
    (str (Files/createTempDirectory (.toPath (io/file parent)) temp-prefix (into-array FileAttribute [])))))


(defn create-temp-file []
  (str (Files/createTempFile temp-prefix "" (no-file-attributes))))

(defmacro with-temp
  "evaluates the body, then deletes the given file or directory.
  returns the result of the evaluation of the body"
  [f body]
  `(try
     (let [result# ~body]
       result#)
     (finally
       (fs/delete-dir ~f LinkOption/NOFOLLOW_LINKS))))


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

(defn put-if-not-present [m k v]
  (if (contains? m k)
    m
    (assoc m k v)))


(defn parse-int [int-str]
  (Integer/parseInt int-str))

(defn contains-value? [v coll]
  (some #(= % v) coll))

(def iso-formatter (f/formatters :date-time))

(chg/add-encoder DateTime (fn [v jsonGenerator] (.writeString jsonGenerator (f/unparse iso-formatter v))))

(defn to-json [v]
  (ch/generate-string v))

(defn buffered [ch]
  (let [result-ch (async/chan 1000)]
    (async/pipe ch result-ch)))

(defn json [data]
  {:headers { "Content-Type" "application/json;charset=UTF-8"}
   :body (to-json data)
   :status 200 })

(defn ok []
  {:headers { "Content-Type" "application/json"}
   :body "{}"
   :status 200 })

(defn fill [coll length filler]
  (let [missing (- length (count coll))]
  (concat coll (replicate missing filler))))

(defn merge-with-k-v [f & maps]
  (when (some identity maps)
    (let [merge-entry (fn [m e]
                        (let [k (key e) v (val e)]
                          (if (contains? m k)
                            (assoc m k (f k (get m k) v))
                            (assoc m k v))))
          merge2 (fn [m1 m2]
                   (reduce merge-entry (or m1 {}) (seq m2)))]
      (reduce merge2 maps))))
