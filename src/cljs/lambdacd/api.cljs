(ns lambdacd.api
  (:require [lambdacd.ajax :as ajax]))

(defn get-build-history []
  (let [result (ajax/GET "api/builds/")]
    result))

(defn get-build-state [build-number]
  (let [result (ajax/GET (str "api/builds/" build-number "/"))]
    result))

(defn trigger [trigger-id data]
  (ajax/POST (str "api/dynamic/" trigger-id) data))