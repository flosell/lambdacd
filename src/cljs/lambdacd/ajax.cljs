(ns lambdacd.ajax
  (:require
    [ajax.core :as ac]
    [cljs.core.async :as async]))

(defn GET [url]
  (let [ch (async/chan 1)]
    (ac/GET url {:handler (fn [response]
                              (async/put! ch response)
                              (async/close! ch))
                   :keywords? true
                   :response-format :json})
    ch))