(ns lambdacd.db
  (:require-macros [reagent.ratom :refer [reaction]])
  (:require [re-frame.core :as re-frame]
            [lambdacd.state :as state]
            [lambdacd.utils :as utils]))


(def default-db
  {:history []
   :pipeline-state []
   :connection-state :lost
   :displayed-build-number nil
   :raw-step-results-visible false
   :expanded-step-ids #{}})

(defn initialize-db-handler [_ _]
  default-db)

(defn- most-recent-build-number [state]
  (->> state
       (map :build-number)
       (sort)
       (last)))

(defn- set-connection-state-active [db]
  (assoc db :connection-state :active))

(defn- add-missing-build-number [db new-history]
  (if (nil? (:displayed-build-number db))
    (assoc db :displayed-build-number (most-recent-build-number new-history))
    db))

(defn history-updated-handler [db [_ new-history]]
  (-> db
      (assoc :history new-history)
      (add-missing-build-number new-history)
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

(defn raw-step-result-visible-subscription [db _]
  (reaction (:raw-step-results-visible @db)))

(defn toggle-raw-step-results-visible-handler [db [_ _]]
  (let [is-visible (:raw-step-results-visible db)]
    (assoc db :raw-step-results-visible (not is-visible))))

(defn current-step-result-subscription [db _]
  (reaction (state/find-by-step-id (:pipeline-state @db) (:step-id @db))))

(defn expanded-step-ids-subscription [db _]
  (reaction (:expanded-step-ids @db)))

(defn toggle-step-expanded[db [_ step-id]]
  (let [cur-expanded (:expanded-step-ids db)
        result (if (contains? cur-expanded step-id)
                 (disj cur-expanded step-id)
                 (conj cur-expanded step-id))]
    (assoc db :expanded-step-ids result)))


(re-frame/register-handler ::history-updated history-updated-handler)
(re-frame/register-handler ::initialize-db initialize-db-handler)
(re-frame/register-handler ::pipeline-state-updated pipeline-state-updated-handler)
(re-frame/register-handler ::connection-lost lost-connection-handler)
(re-frame/register-handler ::build-number-updated build-number-update-handler)
(re-frame/register-handler ::step-id-updated step-id-update-handler)
(re-frame/register-handler ::toggle-raw-step-results-visible toggle-raw-step-results-visible-handler)
(re-frame/register-handler ::toggle-step-expanded toggle-step-expanded)

(re-frame/register-sub ::history history-subscription)
(re-frame/register-sub ::pipeline-state pipeline-state-subscription)
(re-frame/register-sub ::connection-state connection-state-subscription)
(re-frame/register-sub ::build-number build-number-subscription)
(re-frame/register-sub ::step-id step-id-subscription)
(re-frame/register-sub ::raw-step-results-visible raw-step-result-visible-subscription)
(re-frame/register-sub ::current-step-result current-step-result-subscription)
(re-frame/register-sub ::expanded-step-ids expanded-step-ids-subscription)