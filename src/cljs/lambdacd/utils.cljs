(ns lambdacd.utils
  (:require [cljs.core.async :as async]
            [clojure.string :as string]
            [clojure.walk :as walk]))

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

; from clojure.walk/stringify-keys but fixes keywords with slashes and renders them as :kw

(defn stringify-keys [m]
  (let [f (fn [[k v]] (if (keyword? k) [(str k) v] [k v]))]
    (walk/postwalk (fn [x] (if (map? x) (into {} (map f x)) x)) m)))

(defn pretty-print-map [m]
  (-> m
      (stringify-keys)
      (clj->js)
      (js/JSON.stringify nil 2)))
