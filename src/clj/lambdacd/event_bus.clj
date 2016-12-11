(ns lambdacd.event-bus
  (:require [clojure.core.async :as async]
            [lambdacd.event-bus-new :as new]
            [lambdacd.event-bus-legacy :as legacy]))

(defn use-new-event-bus? [ctx] ; only public for macro
  (get-in ctx [:config :use-new-event-bus]))

(defn initialize-event-bus [ctx]
  (if (use-new-event-bus? ctx)
    (new/initialize-event-bus ctx)
    (legacy/initialize-event-bus ctx)))

(defmacro publish! [ctx topic payload]
  `(if (use-new-event-bus? ~ctx)
     (new/publish! ~ctx ~topic ~payload)
     (legacy/publish! ~ctx ~topic ~payload)))

(defmacro publish!! [ctx topic payload]
  `(if (use-new-event-bus? ~ctx)
     (new/publish!! ~ctx ~topic ~payload)
     (legacy/publish!! ~ctx ~topic ~payload)))

(defn subscribe [ctx topic]
  (if (use-new-event-bus? ctx)
    (new/subscribe ctx topic)
    (legacy/subscribe ctx topic)))

(defn only-payload [subscription]
  (let [result-ch (async/chan)]
    (async/go-loop []
      (if-let [result (async/<! subscription)]
        (do
          (async/>! result-ch (:payload result))
          (recur))))
    result-ch))

(defn unsubscribe [ctx topic subscription]
  (if (use-new-event-bus? ctx)
    (new/unsubscribe ctx topic subscription)
    (legacy/unsubscribe ctx topic subscription)))
