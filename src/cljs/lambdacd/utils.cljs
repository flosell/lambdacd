(ns lambdacd.utils
  (:require [cljs.core.async :as async]))

(defn timeout [ms]
  (let [c (async/chan)]
    (js/setTimeout (fn [] (async/close! c)) ms)
    c))