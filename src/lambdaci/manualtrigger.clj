(ns lambdaci.manualtrigger
  (:require [lambdaci.dsl :as dsl]
            [clojure.core.async :as async]))

(def ids-posted-to (atom #{}))

(defn post-id [id]
  (swap! ids-posted-to #(conj %1 id)))

(defn was-posted? [id]
  (contains? @ids-posted-to id))

(defn wait-for-async [p]
  (let [status-ch (async/chan 10)
        result {:status status-ch}
        waiting-future (future (dsl/wait-for p))]
    (async/>!! status-ch :waiting)
    (async/thread (async/>!! status-ch (:status @waiting-future)))
    result))

(defn wait-for-manual-trigger [& _]
  (let [id (str (java.util.UUID/randomUUID))]
    (println "waititing for manual trigger, POST to id" id) ;; try returning a result with status-channel and the id, see what the UI can do with it?
    (assoc (wait-for-async #(was-posted? id)) :trigger-id id)))
