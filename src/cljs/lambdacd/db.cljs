(ns lambdacd.db
  (:require [re-frame.core :as re-frame]
            [lambdacd.state :as state]))

(def default-db
  {:history nil
   :pipeline-state nil
   :connection-state :lost
   :update-history-in-progress? false
   :update-pipeline-in-progress? false
   :displayed-build-number nil
   :raw-step-results-visible false
   :expanded-step-ids #{}
   :expand-active true
   :expand-failures false})

(defn- assoc-if [h b & args]
  (if b
    (apply assoc h args)
    h))

(defn- not-nil? [x]
  (not (nil? x)))

(defn initialize-db-handler [_ [_ {expand-active-default :expand-active-default
                                   expand-failures-default :expand-failures-default :as y}]]
  (-> default-db
      (assoc-if (not-nil? expand-active-default) :expand-active expand-active-default)
      (assoc-if (not-nil? expand-active-default) :expand-failures expand-failures-default)))

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
  (:history db))

(defn pipeline-state-updated-handler [db [_ new-history]]
  (-> db
      (assoc :pipeline-state new-history)
      (set-connection-state-active)))

(defn pipeline-state-subscription [db _]
  (:pipeline-state db))

(defn lost-connection-handler [db _]
  (assoc db :connection-state :lost))

(defn connection-state-subscription [db _]
  (:connection-state db))

(defn update-in-progress?-subscription [db _]
  (or (:update-pipeline-in-progress? db)
      (:update-history-in-progress? db)))

(defn build-number-subscription [db _]
  (:displayed-build-number db))

(defn build-number-update-handler [db [_ new-buildnumber]]
  (-> db
      (assoc :displayed-build-number new-buildnumber)
      (assoc :pipeline-state nil)))

(defn step-id-subscription [db _] ; TODO: maybe we don't need this in the long run and can instead just subscribe on the current step result?
  (:step-id db))

(defn step-id-update-handler [db [_ new-buildnumber]]
  (assoc db :step-id new-buildnumber))

(defn raw-step-result-visible-subscription [db _]
  (:raw-step-results-visible db))

(defn toggle-raw-step-results-visible-handler [db [_ _]]
  (let [is-visible (:raw-step-results-visible db)]
    (assoc db :raw-step-results-visible (not is-visible))))

(defn current-step-result-subscription [db _]
  (state/find-by-step-id (:pipeline-state db) (:step-id db)))

(defn- expand-active? [db step-id]
  (and
    (:expand-active db)
    (state/is-active? (state/find-by-step-id (:pipeline-state db) step-id))))

(defn- expand-failed? [db step-id]
  (and
    (:expand-failures db)
    (state/is-failure? (state/find-by-step-id (:pipeline-state db) step-id))))

(defn step-expanded-subscription [db [_ step-id]]
  (or
    (contains? (:expanded-step-ids db) step-id)
    (expand-active? db step-id)
    (expand-failed? db step-id)))

(defn all-step-ids [{state :pipeline-state}]
  (->> state
       (state/flatten-state)
       (map :step-id)
       (into #{})))

(defn all-expanded-subscription [db _]
  (= (all-step-ids db) (:expanded-step-ids db)))

(defn all-collapsed-subscription [db _]
  (empty? (:expanded-step-ids db)))

(defn toggle-step-expanded [db [_ step-id]]
  (let [cur-expanded (:expanded-step-ids db)
        result (if (contains? cur-expanded step-id)
                 (disj cur-expanded step-id)
                 (conj cur-expanded step-id))]
    (assoc db :expanded-step-ids result)))

(defn set-all-expanded-handler [db _]
  (assoc db :expanded-step-ids (all-step-ids db)))

(defn set-all-collapsed-handler [db _]
  (assoc db :expanded-step-ids #{}))

(defn toggle-expand-active-handler [db _]
  (update db :expand-active not))

(defn toggle-expand-failure-handler [db _]
  (update db :expand-failures not))

(defn expand-active-active-subscription [db _]
  (:expand-active db))

(defn expand-failure-active-subscription [db _]
  (:expand-failures db))

(re-frame/reg-event-db ::history-updated history-updated-handler)
(re-frame/reg-event-db ::initialize-db initialize-db-handler)
(re-frame/reg-event-db ::pipeline-state-updated pipeline-state-updated-handler)
(re-frame/reg-event-db ::connection-lost lost-connection-handler)
(re-frame/reg-event-db ::build-number-updated build-number-update-handler)
(re-frame/reg-event-db ::step-id-updated step-id-update-handler)
(re-frame/reg-event-db ::toggle-raw-step-results-visible toggle-raw-step-results-visible-handler)
(re-frame/reg-event-db ::toggle-step-expanded toggle-step-expanded)
(re-frame/reg-event-db ::set-all-expanded set-all-expanded-handler)
(re-frame/reg-event-db ::set-all-collapsed set-all-collapsed-handler)
(re-frame/reg-event-db ::toggle-expand-active toggle-expand-active-handler)
(re-frame/reg-event-db ::toggle-expand-failures toggle-expand-failure-handler)

(re-frame/reg-sub ::history history-subscription)
(re-frame/reg-sub ::pipeline-state pipeline-state-subscription)
(re-frame/reg-sub ::connection-state connection-state-subscription)
(re-frame/reg-sub ::update-in-progress? update-in-progress?-subscription)
(re-frame/reg-sub ::build-number build-number-subscription)
(re-frame/reg-sub ::step-id step-id-subscription)
(re-frame/reg-sub ::raw-step-results-visible raw-step-result-visible-subscription)
(re-frame/reg-sub ::current-step-result current-step-result-subscription)
(re-frame/reg-sub ::step-expanded? step-expanded-subscription)
(re-frame/reg-sub ::all-expanded? all-expanded-subscription)
(re-frame/reg-sub ::all-collapsed? all-collapsed-subscription)
(re-frame/reg-sub ::expand-active-active? expand-active-active-subscription)
(re-frame/reg-sub ::expand-failures-active? expand-failure-active-subscription)
