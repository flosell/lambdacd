(ns lambdacd.event-bus
  (:require [clojure.core.async :as async]))

(defn initialize-event-bus [ctx]
  (let [publisher-ch (async/chan)
        publication (async/pub publisher-ch :topic)]
    (assoc ctx :event-publisher   publisher-ch
               :event-publication publication)))

(defmacro publish! [ctx topic payload]
  `(async/>! (:event-publisher ~ctx) {:topic ~topic :payload ~payload}))

(defmacro publish!! [ctx topic payload]
  `(async/>!! (:event-publisher ~ctx) {:topic ~topic :payload ~payload}))

(defn subscribe [ctx topic]
  (let [result-ch (async/chan)]
    (async/sub (:event-publication ctx) topic result-ch)
    result-ch))

(defn only-payload [subscription]
  (let [result-ch (async/chan)]
    (async/go-loop []
      (if-let [result (async/<! subscription)]
        (do
          (async/>! result-ch (:payload result))
          (recur))))
    result-ch))

(defn- drain [ch]
  (async/go-loop []
    (if (async/<! ch)
      (recur))))

(defn unsubscribe [ctx topic subscription]
  (async/unsub (:event-publication ctx) topic subscription)
  ; drain channel since maybe a publisher wrote to the subscription between the last read from it and the unsubscribe
  (drain subscription))
