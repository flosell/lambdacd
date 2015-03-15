(ns lambdacd.api
  (:require [lambdacd.ajax :as ajax]))

(defn get-build-history []
  (let [result (ajax/GET "api/builds/")]
    result))