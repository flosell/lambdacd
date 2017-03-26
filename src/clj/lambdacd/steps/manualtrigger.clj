(ns lambdacd.steps.manualtrigger
  "Build step that waits for manual user interaction.

  Example:
  ```clojure
  > (wait-for-manual-trigger args ctx) ; Blocks, but setting `:trigger-id` in step-result to a random UUID
  > (post-id ctx trigger-id trigger-parameters) ; Returns immediately, unblocks the waiting manual trigger
  ```
  "
  (:require [clojure.core.async :as async]
            [clojure.tools.logging :as log]
            [lambdacd.event-bus :as event-bus]
            [lambdacd.steps.support :as support]
            [lambdacd.stepsupport.killable :as killable])
  (:import (java.util UUID)))

(defn post-id
  "Entrypoint for UI and others to release a waiting trigger identified by an ID."
  [ctx id trigger-parameters]
  (log/info "received parameterized trigger with id " id " with data " trigger-parameters)
  (event-bus/publish!! ctx :manual-trigger-received {:trigger-id         id
                                                   :trigger-parameters trigger-parameters}))

(defn- wait-for-trigger-event-while-not-killed [ctx trigger-events expected-trigger-id]
  (loop []
    (let [[result _] (async/alts!! [trigger-events
                                    (async/timeout 1000)] :priority true)]
      (killable/if-not-killed ctx
        (if (and result (= expected-trigger-id (:trigger-id result)))
          (assoc (:trigger-parameters result) :status :success)
          (recur))))))

(defn ^{:display-type :manual-trigger} wait-for-manual-trigger
  "Build step that waits for someone to trigger a build manually, usually by clicking a button in a UI that supports it."
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

(defn parameterized-trigger
  "Same as `wait-for-manual-trigger` but also sets metadata that instructs a supporting UI to ask the user for parameters
  that will be sent and returned.

  Example:
  ```clojure
  > (parameterized-trigger {:version {:desc \"version to deploy\"}} ctx) ; blocks until post-id is called
  {:status :success
   :version {:version \"some-version\"}}
  > (post-id ctx trigger-id {:version \"some-version\"})
  ```
  "

  [parameter-config ctx]
  (async/>!! (:result-channel ctx) [:parameters parameter-config])
  (wait-for-manual-trigger nil ctx))
