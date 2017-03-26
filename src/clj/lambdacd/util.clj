(ns lambdacd.util
  {:deprecated "0.12.1"}
  (:import (java.nio.file.attribute FileAttribute))
  (:require [clojure.data.json :as json]
            [lambdacd.util.internal.bash :as bash-utils]
            [lambdacd.util.internal.map :as map-utils]
            [lambdacd.util.internal.sugar :as sugar-utils]
            [lambdacd.ui.internal.util :as ui-utils]
            [lambdacd.util.internal.async :as async-utils]
            [lambdacd.util.internal.temp :as temp-utils]
            [lambdacd.util.internal.coll :as coll-utils]))

(defn range-from
  "UNUSED AND DEPRECATED, WILL BE REMOVED SOON" ; only used in tests
  {:deprecated "0.12.1"}
  [from len]
  (range (inc from) (+ (inc from) len)))

(defn no-file-attributes
  "DEPRECATED, WILL BE REMOVED SOON"
  {:deprecated "0.12.1"}
  []
  (into-array FileAttribute []))


(def ^{:doc "DEPRECATED, WILL BE REMOVED SOON"
       :deprecated "0.12.1"} temp-prefix temp-utils/temp-prefix) ; DEPRECATED, WILL BE REMOVED SOON

(defn create-temp-dir
  "DEPRECATED, WILL BE REMOVED SOON"
  {:deprecated "0.12.1"}
  ([]
    (temp-utils/create-temp-dir))
  ([parent]
   (temp-utils/create-temp-dir parent)))


(defn create-temp-file
  "DEPRECATED, WILL BE REMOVED SOON"
  {:deprecated "0.12.1"}
  []
  (temp-utils/create-temp-file))

(defmacro with-temp
  "DEPRECATED, WILL BE REMOVED SOON
  evaluates the body, then deletes the given file or directory.
  returns the result of the evaluation of the body"
  {:deprecated "0.12.1"}
  [f body]
  `(temp-utils/with-temp ~f ~body))


(defn write-as-json
  "UNUSED AND DEPRECATED, WILL BE REMOVED SOON"
  {:deprecated "0.12.1"}
  [file data]
  (spit file (json/write-str data)))

(defn bash
  "DEPRECATED, WILL BE REMOVED SOON"
  {:deprecated "0.12.1"}
  [cwd & commands]
  (apply bash-utils/bash cwd commands))


(defn map-if
  "UNUSED AND DEPRECATED, WILL BE REMOVED SOON" ; only used by test
  {:deprecated "0.12.1"}
  [pred f coll]
  (map #(if (pred %)
         (f %)
         %) coll))

(defn put-if-not-present
  "DEPRECATED, WILL BE REMOVED SOON"
  {:deprecated "0.12.1"}
  [m k v]
  (map-utils/put-if-not-present m k v))

(defn parse-int
  "DEPRECATED, WILL BE REMOVED SOON"
  {:deprecated "0.12.1"}
  [int-str]
  (sugar-utils/parse-int int-str))

(defn contains-value?
  "DEPRECATED, WILL BE REMOVED SOON"
  {:deprecated "0.12.1"}
  [v coll]
  (map-utils/contains-value? v coll))

(defn to-json
  "DEPRECATED, WILL BE REMOVED SOON"
  {:deprecated "0.12.1"}
  [v]
  (ui-utils/to-json v))

(defn buffered
  "DEPRECATED, WILL BE REMOVED SOON"
  {:deprecated "0.12.1"}
  [ch]
  (async-utils/buffered ch))

(defn json
  "DEPRECATED, WILL BE REMOVED SOON"
  {:deprecated "0.12.1"}
  [data]
  (ui-utils/json data))

(defn ok
  "UNUSED AND DEPRECATED, WILL BE REMOVED SOON"
  {:deprecated "0.12.1"}
  []
  {:headers { "Content-Type" "application/json"}
   :body "{}"
   :status 200 })

(defn fill
  "DEPRECATED, WILL BE REMOVED SOON"
  {:deprecated "0.12.1"}
  [coll length filler]
  (coll-utils/fill coll length filler))

(defn merge-with-k-v
  "DEPRECATED, WILL BE REMOVED SOON"
  {:deprecated "0.12.1"}
  [f & maps]
  (apply map-utils/merge-with-k-v f maps))
