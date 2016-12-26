(ns lambdacd.util.internal.async
  (:require [clojure.core.async :as async]))

(defn buffered [ch]
  (let [result-ch (async/chan 100)]
    (async/pipe ch result-ch)))
