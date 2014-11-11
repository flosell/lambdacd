(ns lambdacd.json-model
  "defines conversions between the data-models we use internally and the data-model that's used in JSON
   (which is more user facing)"
  (:import (java.util.regex Pattern))
  (:require [clojure.string :as str]))


(defn formatted-step-id [step-id]
  (str/join "-" step-id ))

(defn unformat-step-id [formatted-step-id]
  (seq (vec (map read-string (str/split formatted-step-id (Pattern/compile "-")))))) ; TODO: seq-vec: dirty hack because json-formatting for UI can't handle lazy-seq

(defn- step-result->json-format [[k v]]
  {:step-id (formatted-step-id k) :step-result v})

(defn pipeline-state->json-format [pipeline-state]
  (map step-result->json-format pipeline-state))

(defn- step-json->step [{step-result :step-result step-id :step-id}]
  {(unformat-step-id step-id) step-result})

(defn json-format->pipeline-state [json-map]
  (into {} (map step-json->step json-map)))