(ns lambdacd.manualtrigger
  (:require [lambdacd.execution :as execution]
            [clojure.tools.logging :as log]
            ))

(def ids-posted-to (atom {}))

(defn post-id [id trigger-parameters]
  (log/info "received parameterized trigger with id " id " with data " trigger-parameters)
  (swap! ids-posted-to #(assoc %1 id trigger-parameters)))

(defn- was-posted? [id]
  (get @ids-posted-to id))

(defn- wait-for-trigger [id ctx]
  (execution/send-output ctx :out (str "Waiting for manual trigger... nothing to see here"))
  (loop []
    (let [trigger-parameters (was-posted? id)]
      (if @(:is-killed ctx)
        (do
          (execution/send-output ctx :status :killed)
          {:status :killed})
        (if trigger-parameters
          trigger-parameters
          (do (Thread/sleep 1000)
              (recur)))))))

(defn wait-for-manual-trigger
  "build step that waits for someone to trigger the build by POSTing to the url indicated by a random trigger id.
  the trigger-id is returned as the :trigger-id result value. see UI implementation for details"
  [_ ctx & _]
  (let [id (str (java.util.UUID/randomUUID))]
    (execution/send-output ctx :trigger-id id)
    (execution/send-output ctx :status :waiting)
    (assoc (wait-for-trigger id ctx) :status :success)))

(defn parameterized-trigger [parameter-config ctx]
  (execution/send-output ctx :parameters parameter-config)
  (wait-for-manual-trigger nil ctx))