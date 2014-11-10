(ns lambdacd.json-model
  "defines conversions between the data-models we use internally and the data-model that's used in JSON
   (which is more user facing)"
  (:require [clojure.string :as str]))


(defn formatted-step-id [step-id]
  (str/join "-" step-id ))

(defn- step-result->json-format [[k v]]
  {:step-id (formatted-step-id k) :step-result v})

(defn pipeline-state->json-format [pipeline-state]
  (map step-result->json-format pipeline-state))