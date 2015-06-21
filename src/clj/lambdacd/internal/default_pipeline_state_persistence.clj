(ns lambdacd.internal.default-pipeline-state-persistence
  "defines conversions between the data-models we use internally and the data-model that's used in JSON
   (which is more user facing)"
  (:import (java.util.regex Pattern)
           (java.io File)
           (org.joda.time DateTime))
  (:require [clojure.string :as str]
            [lambdacd.util :as util]
            [clojure.java.io :as io]
            [clj-time.format :as f]
            [clj-time.coerce :as c]
            [cheshire.core :as ch]
            [cheshire.generate :as chg]
            [clojure.data.json :as json]))


(defn- formatted-step-id [step-id]
  (str/join "-" step-id ))

(defn- unformat-step-id [formatted-step-id]
  (map util/parse-int (str/split formatted-step-id (Pattern/compile "-"))))

(defn- step-result->json-format [[k v]]
  {:step-id (formatted-step-id k) :step-result v})

(defn- pipeline-state->json-format [pipeline-state]
  (map step-result->json-format pipeline-state))

(defn- step-json->step [{step-result :step-result step-id :step-id}]
  {(unformat-step-id step-id) step-result})

(defn- json-format->pipeline-state [json-map]
  (into {} (map step-json->step json-map)))


(defn- to-date-if-date [v]
  (try
    (f/parse util/iso-formatter v)
    (catch Throwable t v)))

(defn- post-process-values [k v]
  (if (= :status k)
    (keyword v)
    (to-date-if-date v)))

(defn- read-state [filename]
  (let [build-number (util/parse-int (second (re-find #"build-(\d+)" filename)))
        state (json-format->pipeline-state (json/read-str (slurp filename) :key-fn keyword :value-fn post-process-values))]
    { build-number state }))

(defn write-build-history [home-dir build-number new-state]
  (if home-dir
    (let [dir (str home-dir "/" "build-" build-number "/")
          path (str dir "pipeline-state.json")
          state-as-json (pipeline-state->json-format (get new-state build-number))
          state-as-json-string (util/to-json state-as-json)]
      (.mkdirs (io/file dir))
      (spit path state-as-json-string))))

(defn read-build-history-from [home-dir]
  (let [dir (io/file home-dir)
        home-contents (file-seq dir)
        directories-in-home (filter #(.isDirectory %) home-contents)
        build-dirs (filter #(.startsWith (.getName %) "build-") directories-in-home)
        build-state-files (map #(str % "/pipeline-state.json") build-dirs)
        states (map read-state build-state-files)]
    (into {} states)))
