(ns lambdacd.utils
  (:require [cljs.core.async :as async]))

(defn timeout [ms]
  (let [c (async/chan)]
    (js/setTimeout (fn [] (async/close! c)) ms)
    c))

(defn click-handler [handler]
  (fn [evt]
    (handler)
    (.stopPropagation evt)))

(defn append-components [a b]
  (into [] (concat a b)))
