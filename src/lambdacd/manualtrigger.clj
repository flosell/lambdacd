(ns lambdacd.manualtrigger
  (:require [lambdacd.execution :as execution]
            [clojure.core.async :as async]))

(def ids-posted-to (atom #{}))

(defn post-id [id]
  (swap! ids-posted-to #(conj %1 id)))

(defn- was-posted? [id]
  (contains? @ids-posted-to id))

(defn- wait-for-async [p]
  (let [status-ch (async/chan 10)
        result {:status status-ch}
        waiting-future (future (execution/wait-for p))]
    (async/>!! status-ch :waiting)
    (async/thread (async/>!! status-ch (:status @waiting-future)))
    result))

(defn wait-for-manual-trigger
  "build step that waits for someone to trigger the build by POSTing to the url indicated by a random trigger id.
  the trigger-id is returned as the :trigger-id result value. see UI implementation for details"
  [& _]
  (let [id (str (java.util.UUID/randomUUID))]
    (assoc (wait-for-async #(was-posted? id)) :trigger-id id)))
