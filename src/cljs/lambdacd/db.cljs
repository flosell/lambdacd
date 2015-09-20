(ns lambdacd.db
  (:require-macros [reagent.ratom :refer [reaction]])
  (:require [re-frame.core :as re-frame]))


(def default-db
  {:history []
   :pipeline-state []
   :connection-state :lost
   :displayed-build-number nil})

(defn initialize-db-handler [_ _]
  default-db)


(defn- set-connection-state-active [db]
  (assoc db :connection-state :active))

(defn history-updated-handler [db [_ new-history]]
  (-> db
      (assoc :history new-history)
      (set-connection-state-active)))

(defn history-subscription [db _]
  (reaction (:history @db)))

(defn pipeline-state-updated-handler [db [_ new-history]]
  (-> db
      (assoc :pipeline-state new-history)
      (set-connection-state-active)))

(defn pipeline-state-subscription [db _]
  (reaction (:pipeline-state @db)))

(defn lost-connection-handler [db _]
  (assoc db :connection-state :lost))

(defn connection-state-subscription [db _]
  (reaction (:connection-state @db)))

(defn build-number-subscription [db _]
  (reaction (:displayed-build-number @db)))

(defn build-number-update-handler [db [_ new-buildnumber]]
  (assoc db :displayed-build-number new-buildnumber))

(defn step-id-subscription [db _] ; TODO: maybe we don't need this in the long run and can instead just subscribe on the current step result?
  (reaction (:step-id @db)))

(defn step-id-update-handler [db [_ new-buildnumber]]
  (assoc db :step-id new-buildnumber))


(re-frame/register-handler ::history-updated history-updated-handler)
(re-frame/register-handler ::initialize-db initialize-db-handler)
(re-frame/register-handler ::pipeline-state-updated pipeline-state-updated-handler)
(re-frame/register-handler ::connection-lost lost-connection-handler)
(re-frame/register-handler ::build-number-updated build-number-update-handler)
(re-frame/register-handler ::step-id-updated step-id-update-handler)

(re-frame/register-sub ::history history-subscription)
(re-frame/register-sub ::pipeline-state pipeline-state-subscription)
(re-frame/register-sub ::connection-state connection-state-subscription)
(re-frame/register-sub ::build-number build-number-subscription)
(re-frame/register-sub ::step-id step-id-subscription)