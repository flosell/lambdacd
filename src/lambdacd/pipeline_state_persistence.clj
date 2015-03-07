(ns lambdacd.pipeline-state-persistence
  "defines conversions between the data-models we use internally and the data-model that's used in JSON
   (which is more user facing)"
  (:import (java.util.regex Pattern)
           (java.io File))
  (:require [clojure.string :as str]
            [lambdacd.util :as util]
            [clojure.java.io :as io]
            [clojure.data.json :as json]))


(defn formatted-step-id [step-id]
  (str/join "-" step-id ))

(defn unformat-step-id [formatted-step-id]
  (map read-string (str/split formatted-step-id (Pattern/compile "-"))))

(defn- step-result->json-format [[k v]]
  {:step-id (formatted-step-id k) :step-result v})

(defn pipeline-state->json-format [pipeline-state]
  (map step-result->json-format pipeline-state))

(defn- step-json->step [{step-result :step-result step-id :step-id}]
  {(unformat-step-id step-id) step-result})

(defn json-format->pipeline-state [json-map]
  (into {} (map step-json->step json-map)))

(defn write-state-to-disk [home-dir build-number new-state]
  (if home-dir
    (let [dir (str home-dir "/" "build-" build-number "/")
          path (str dir "pipeline-state.json")
          state-as-json (pipeline-state->json-format (get new-state build-number))]
      (.mkdirs (io/file dir))
      (util/write-as-json path state-as-json))))

(defn read-state [filename]
  (let [build-number (read-string (second (re-find #"build-(\d+)" filename)))
        state (json-format->pipeline-state (json/read-str (slurp filename) :key-fn keyword))]
    { build-number state }))