(ns lambdacd.utils
  (:require [cljs.core.async :as async]
            [clojure.string :as string]))

(defn click-handler [handler]
  (fn [evt]
    (handler)
    (.stopPropagation evt)))

(defn append-components [a b]
  (vec (concat a b)))

(defn classes [& cs]
  (if (vector? (first cs))
    (apply string/join " " cs)
    (string/join " " cs)))

(defn put-if-not-present [m k v]
  (if (contains? m k)
    m
    (assoc m k v)))