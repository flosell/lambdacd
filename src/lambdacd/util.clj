(ns lambdacd.util
  (:import (java.nio.file Files)
           (java.nio.file.attribute FileAttribute))
  (:require [clojure.core.async :as async]
            [clojure.java.io :as io]
            [clojure.string :as string]
            [clojure.java.shell :as jsh]
            [clojure.tools.logging :as log]
            [clojure.data.json :as json]))

(defn range-from [from len] (range (inc from) (+ (inc from) len)))

(defn is-channel? [c]
  (satisfies? clojure.core.async.impl.protocols/Channel c))

(defn create-temp-dir []
  (str (Files/createTempDirectory "foo" (into-array FileAttribute []))))

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


(defn append-to-ch [result-ch v]
  (let [additional-value-channel (async/chan 1)
        merged-channels (async/merge [result-ch additional-value-channel])]
    (do
      (async/>!! additional-value-channel v)
      merged-channels)))

(defn append-tuple-to-ch [ch key value]
  (append-to-ch ch [key value]))


;; TODO: this shouldn't actually exist, we should preprocess this somewhere else
(defn- serialize-channel [k v]
  (if (is-channel? v)
    :waiting
    v))
(defn json [data]
  { :headers { "Content-Type" "application/json"}
   :body (json/write-str data :value-fn serialize-channel)
   :status 200 })

