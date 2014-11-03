(ns lambdacd.manualtrigger
  (:require [lambdacd.execution :as execution]
            [clojure.core.async :as async]))

(def ids-posted-to (atom #{}))

(defn post-id [id]
  (swap! ids-posted-to #(conj %1 id)))

(defn- was-posted? [id]
  (contains? @ids-posted-to id))

(defn- wait-for-async [p]
  (let [result-ch (async/chan 10)
        waiting-future (future (execution/wait-for p))]
    (async/>!! result-ch [:status :waiting])
    (async/thread (async/>!! result-ch [:status (:status @waiting-future)]))
    result-ch))

(defn wait-for-manual-trigger
  "build step that waits for someone to trigger the build by POSTing to the url indicated by a random trigger id.
  the trigger-id is returned as the :trigger-id result value. see UI implementation for details"
  [& _]
  (let [id (str (java.util.UUID/randomUUID))
        ch-wait(wait-for-async #(was-posted? id))]
    (async/>!! ch-wait [:trigger-id id])
    ch-wait))