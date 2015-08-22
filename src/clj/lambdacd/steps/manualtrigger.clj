(ns lambdacd.steps.manualtrigger
  (:require [clojure.core.async :as async]
            [clojure.tools.logging :as log]
            [lambdacd.event-bus :as event-bus]
            [lambdacd.steps.support :as support])
  (:import (java.util UUID)))

(defn post-id [ctx id trigger-parameters]
  (log/info "received parameterized trigger with id " id " with data " trigger-parameters)
  (event-bus/publish ctx :manual-trigger-received {:trigger-id id
                                                   :trigger-parameters trigger-parameters}))

(defn- wait-for-trigger [id ctx]
  (async/>!! (:result-channel ctx) [:out (str "Waiting for trigger..." )])
  (let [subscription   (event-bus/subscribe ctx :manual-trigger-received)
        trigger-events (event-bus/only-payload subscription)
        wait-result (loop []
                      (let [[result _] (async/alts!! [trigger-events
                                               (async/timeout 1000)] :priority true)]
                        (support/if-not-killed ctx
                                               (if (and result (= id (:trigger-id result)))
                                                 (assoc (:trigger-parameters result) :status :success)
                                                 (recur)))))]
    (event-bus/unsubscribe ctx :manual-trigger-received subscription)
    wait-result))

(defn wait-for-manual-trigger
  "build step that waits for someone to trigger the build by POSTing to the url indicated by a random trigger id.
  the trigger-id is returned as the :trigger-id result value. see UI implementation for details"
  [_ ctx & _]
  (let [id (str (UUID/randomUUID))
        result-ch (:result-channel ctx)]
    (async/>!! result-ch [:trigger-id id])
    (async/>!! result-ch [:status :waiting])
    (wait-for-trigger id ctx)))

(defn parameterized-trigger [parameter-config ctx]
  (async/>!! (:result-channel ctx) [:parameters parameter-config])
  (wait-for-manual-trigger nil ctx))
