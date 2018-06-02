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
           (let [update-in-progress? @(re-frame/subscribe [::db/update-in-progress?])]
             (when-not update-in-progress?
               (re-frame/dispatch [::tick]))
             (async/<! (async/timeout poll-frequency))
             (recur))))

(defn on-tick [db _]
  (re-frame/dispatch [::start-update-history])
  (if (:displayed-build-number db)
    (re-frame/dispatch [::update-pipeline-state]))
  db)

(defn start-update-history-handler [{:keys [db]} _]
  {:db (assoc db :update-in-progress? true)
   :dispatch [::update-history]})

(defn update-history-handler [db _]
  (go
    (let [response (async/<! (api/get-build-history))
          data (:response response)
          type (:type response)]
      (re-frame/dispatch [::finish-update-history 
                          (if (= :success type)
                            [::db/history-updated data] 
                            [::db/connection-lost])])))
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

(defn finish-update-history-handler [{:keys [db]} [_ next-action]]
  {:db (assoc db :update-in-progress? false)
   :dispatch next-action})

(re-frame/reg-event-db ::tick on-tick)
(re-frame/reg-event-fx ::start-update-history start-update-history-handler)
(re-frame/reg-event-db ::update-history update-history-handler)
(re-frame/reg-event-db ::update-pipeline-state update-pipeline-state-handler)
(re-frame/reg-event-fx ::finish-update-history finish-update-history-handler)
