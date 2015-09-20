(ns lambdacd.subs
    (:require-macros [reagent.ratom :refer [reaction]])
    (:require [re-frame.core :as re-frame]))


(defn history-subscription [db _]
  (reaction (:history @db)))


(defn state-subscription [db _]
  (reaction (:pipeline-state @db)))



(re-frame/register-sub :history history-subscription)
(re-frame/register-sub :state state-subscription)

(re-frame/register-sub
 :name
 (fn [db]
   (reaction (:name @db))))

(re-frame/register-sub
 :active-panel
 (fn [db _]
   (reaction (:active-panel @db))))
