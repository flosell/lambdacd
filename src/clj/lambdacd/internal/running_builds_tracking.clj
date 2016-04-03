(ns lambdacd.internal.running-builds-tracking
  (:require [lambdacd.event-bus :as event-bus]
            [clojure.core.async :as async]))

(defn running-step-record [payload]
  {:step-id      (:step-id payload)
   :build-number (:build-number payload)})

(defn initialize-running-builds-tracking [ctx]
  (let [steps-started-subscription  (event-bus/subscribe ctx :step-started)
        steps-started-payload       (event-bus/only-payload steps-started-subscription)

        steps-finished-subscription (event-bus/subscribe ctx :step-finished)
        steps-finished-payload      (event-bus/only-payload steps-finished-subscription)
        started-steps               (atom #{})]
    (async/go-loop []
      (if-let [payload (async/<! steps-started-payload)]
        (do
          (swap! started-steps #(conj % (running-step-record payload)))
                 (recur))))
      (async/go-loop []
        (if-let [payload (async/<! steps-finished-payload)]
          (do
            (swap! started-steps #(disj % (running-step-record payload)))
            (recur))))
      (assoc ctx :started-steps started-steps)))
