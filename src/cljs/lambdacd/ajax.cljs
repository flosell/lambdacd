(ns lambdacd.ajax
  (:require
    [ajax.core :as ac]
    [cljs.core.async :as async]))

(defn GET [url]
  (let [ch (async/chan 1)]
    (ac/GET url {:handler (fn [response]
                              (async/put! ch response)
                              (async/close! ch))
                 :error-handler (fn [response]
                                  (async/close! ch))
                 :keywords? true
                 :response-format :json})
    ch))

(defn POST [url data handler]
  (ac/POST url {:format :json
                :params data
                :handler handler}))