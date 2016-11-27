(ns lambdacd.steps.manualtrigger
  (:require [clojure.core.async :as async]
            [clojure.tools.logging :as log]
            [lambdacd.event-bus :as event-bus]
            [lambdacd.steps.support :as support])
  (:import (java.util UUID)))

(defn post-id [ctx id trigger-parameters]
  (log/info "received parameterized trigger with id " id " with data " trigger-parameters)
  (event-bus/publish!! ctx :manual-trigger-received {:trigger-id         id
                                                   :trigger-parameters trigger-parameters}))

(defn- wait-for-trigger-event-while-not-killed [ctx trigger-events expected-trigger-id]
  (loop []
    (let [[result _] (async/alts!! [trigger-events
                                    (async/timeout 1000)] :priority true)]
      (support/if-not-killed ctx
        (if (and result (= expected-trigger-id (:trigger-id result)))
          (assoc (:trigger-parameters result) :status :success)
          (recur))))))

(defn ^{:display-type :manual-trigger} wait-for-manual-trigger
  "build step that waits for someone to trigger the build by POSTing to the url indicated by a random trigger id.
  the trigger-id is returned as the :trigger-id result value. see UI implementation for details"
  [_ ctx & _]
  (let [trigger-id     (str (UUID/randomUUID))
        result-ch      (:result-channel ctx)
        subscription   (event-bus/subscribe ctx :manual-trigger-received)
        trigger-events (event-bus/only-payload subscription)
        _              (async/>!! result-ch [:trigger-id trigger-id])
        _              (async/>!! result-ch [:status :waiting])
        _              (async/>!! result-ch [:out (str "Waiting for trigger...")])
        wait-result    (wait-for-trigger-event-while-not-killed ctx trigger-events trigger-id)
        _              (event-bus/unsubscribe ctx :manual-trigger-received subscription)]
    wait-result))

(defn parameterized-trigger [parameter-config ctx]
  (async/>!! (:result-channel ctx) [:parameters parameter-config])
  (wait-for-manual-trigger nil ctx))
