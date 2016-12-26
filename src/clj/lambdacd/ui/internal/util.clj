(ns lambdacd.ui.internal.util
  (:require [clj-time.format :as f]
            [cheshire.generate :as chg]
            [cheshire.core :as ch])
  (:import (org.joda.time DateTime)
           (com.fasterxml.jackson.core JsonGenerator)))

(def iso-formatter (f/formatters :date-time))

(chg/add-encoder DateTime (fn [v ^JsonGenerator jsonGenerator] (.writeString jsonGenerator ^String (f/unparse iso-formatter v))))

(defn to-json [v] (ch/generate-string v))

(defn json [data]
  {:headers { "Content-Type" "application/json;charset=UTF-8"}
   :body (to-json data)
   :status 200 })
