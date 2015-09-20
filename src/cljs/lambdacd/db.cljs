(ns lambdacd.db
  (:require-macros [reagent.ratom :refer [reaction]])
  (:require [re-frame.core :as re-frame]))

(def default-db
  {:history []
   :pipeline-state []})

(defn initialize-db-handler [_ _]
  default-db)

(defn history-updated-handler [db [_ new-history]]
  (assoc db :history new-history))

(defn history-subscription [db _]
  (reaction (:history @db)))

(defn pipeline-state-updated-handler [db [_ new-history]]
  (assoc db :pipeline-state new-history))

(defn pipeline-state-subscription [db _]
  (reaction (:pipeline-state @db)))


(re-frame/register-handler ::history-updated history-updated-handler)
(re-frame/register-handler ::initialize-db initialize-db-handler)
(re-frame/register-handler ::pipeline-state-updated pipeline-state-updated-handler)

(re-frame/register-sub ::history history-subscription)
(re-frame/register-sub ::pipeline-state pipeline-state-subscription)