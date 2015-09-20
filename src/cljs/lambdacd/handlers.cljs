(ns lambdacd.handlers
  (:require [re-frame.core :as re-frame]
            [lambdacd.db :as db]))


(defn history-updated-handler [db [_ new-history]]
  (println "new history " new-history)
  (assoc db :history new-history))

(re-frame/register-handler :history-updated history-updated-handler)


(re-frame/register-handler
  :initialize-db
  (fn  [_ _]
    db/default-db))


; example only: FIXME: remove
(re-frame/register-handler
  :set-active-panel
  (fn [db [_ active-panel]]
    (assoc db :active-panel active-panel)))

