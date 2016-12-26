(ns lambdacd.event-bus-new
  (:require [clojure.core.async :as async]
            [lambdacd.util.internal.map :as map-util]))

(defn initialize-event-bus [ctx]
  (assoc ctx :event-bus-chans-and-mults (atom {})))

(defn- ensure-topic [ctx topic]
  (swap! (:event-bus-chans-and-mults ctx) (fn [chans]
                                            (let [ch (async/chan)
                                                  m  (async/mult ch)]
                                              (map-util/put-if-not-present chans topic
                                                                       {:publisher-ch    ch
                                                                        :subscriber-mult m})))))

(defn publisher-ch [ctx topic] ; public only for macro
  (get-in (ensure-topic ctx topic) [topic :publisher-ch]))

(defn- subscriber-mult [ctx topic]
  (get-in (ensure-topic ctx topic) [topic :subscriber-mult]))

(defmacro publish! [ctx topic payload]
  `(async/>! (publisher-ch ~ctx ~topic) {:topic ~topic :payload ~payload}))

(defmacro publish!! [ctx topic payload]
  `(async/>!! (publisher-ch ~ctx ~topic) {:topic ~topic :payload ~payload}))

(defn subscribe [ctx topic]
  (let [result-ch (async/chan)]
    (async/tap (subscriber-mult ctx topic) result-ch)
    result-ch))

(defn- drain [ch]
  (async/go-loop []
    (if (async/<! ch)
      (recur))))

(defn unsubscribe [ctx topic subscription]
  (async/untap (subscriber-mult ctx topic) subscription)
  ; drain channel since maybe a publisher wrote to the subscription between the last read from it and the unsubscribe
  (drain subscription))
