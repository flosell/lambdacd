(ns lambdacd.logic
  (:require-macros [cljs.core.async.macros :refer [go go-loop]])
  (:require [lambdacd.utils :as utils]
            [cljs.core.async :as async]
            [re-frame.core :as re-frame]
            [lambdacd.db :as db]
            [lambdacd.api :as api]))

(def poll-frequency 1000)

(defn start-ticker []
  (go-loop []
           (re-frame/dispatch [::tick])
           (async/<! (utils/timeout poll-frequency))
           (recur)))

(defn on-tick [db _]
  (re-frame/dispatch [::update-history])
  (if (:displayed-build-number db)
    (re-frame/dispatch [::update-pipeline-state]))
  db)

(defn update-history-handler [db _]
  (go
    (let [response (async/<! (api/get-build-history))
          data (:response response)
          type (:type response)]
      (if (= :success type)
        (re-frame/dispatch [::db/history-updated data])
        (re-frame/dispatch [::db/connection-lost]))))
    db)

(defn update-pipeline-state-handler [db _]
  (go
    (let [response (async/<! (api/get-build-state (:displayed-build-number db)))
          data (:response response)
          type (:type response)
          status (:status data)]
      (cond
        (= :success type) (re-frame/dispatch [::db/pipeline-state-updated data])
        (and (= :failure type)
             (= 404 status)) (re-frame/dispatch [::db/build-number-updated nil])
        :else (re-frame/dispatch [::db/connection-lost]))))
  db)


(re-frame/register-handler ::tick on-tick)
(re-frame/register-handler ::update-history update-history-handler)
(re-frame/register-handler ::update-pipeline-state update-pipeline-state-handler)
