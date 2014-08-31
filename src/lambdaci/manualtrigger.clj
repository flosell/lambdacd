(ns lambdaci.manualtrigger
  (:require [lambdaci.dsl :as dsl]))

(def ids-posted-to (atom #{}))

(defn post-id [id]
  (swap! ids-posted-to #(conj %1 id)))

(defn was-posted? [id]
  (contains? @ids-posted-to id))

(defn wait-for-manual-trigger [& _]
  (let [id (str (java.util.UUID/randomUUID))]
    (println "waititing for manual trigger, POST to id" id)
    (dsl/wait-for #(was-posted? id))))
