(ns lambdacd.event-bus
  "Entry-point into the LambdaCD event bus.

  The event-bus exists to decouple communication between various parts of LambdaCD and
  allow external libraries to act on or publish events in a LambdaCD instance.

  Usage example:
  ```clojure
  (let [subscription (subscribe ctx :some-topic)
        payloads (only-payload subscription)]
    (<! (go-loop []
          (if-let [event (<! payloads)]
            (do
              (publish! ctx :some-other-topic (:some-value event))
              (recur)))))
    (unsubscribe ctx :some-topic subscription))
  ```"
  (:require [clojure.core.async :as async]
            [lambdacd.event-bus-new :as new]
            [lambdacd.event-bus-legacy :as legacy]))

(defn ^:no-doc use-new-event-bus? [ctx] ; only public for macro
  (get-in ctx [:config :use-new-event-bus]))

(defn initialize-event-bus
  "Initialize everything necessary for an event-bus to run.
  Returns a ctx that contains everything necessary so others can use the event-bus later on."
  [ctx]
  (if (use-new-event-bus? ctx)
    (new/initialize-event-bus ctx)
    (legacy/initialize-event-bus ctx)))

(defmacro publish!
  "Publish an event on a particular topic of the event-bus when calling from a go block."
  [ctx topic payload]
  `(if (use-new-event-bus? ~ctx)
     (new/publish! ~ctx ~topic ~payload)
     (legacy/publish! ~ctx ~topic ~payload)))

(defmacro publish!!
  "Publish an event on a particular topic of the event-bus when calling from a normal thread."
  [ctx topic payload]
  `(if (use-new-event-bus? ~ctx)
     (new/publish!! ~ctx ~topic ~payload)
     (legacy/publish!! ~ctx ~topic ~payload)))

(defn subscribe
  "Subscribe to a particular topic on the event-bus.
  Returns a subscription channel, i.e. a channel of events and metadata.
  Use only-payload to get a channel of only event payloads."
  [ctx topic]
  (if (use-new-event-bus? ctx)
    (new/subscribe ctx topic)
    (legacy/subscribe ctx topic)))

(defn only-payload
  "Takes a subscription channel and returns a channel that contains only the payload."
  [subscription]
  (let [result-ch (async/chan)]
    (async/go-loop []
      (if-let [result (async/<! subscription)]
        (do
          (async/>! result-ch (:payload result))
          (recur))))
    result-ch))

(defn unsubscribe
  "Unsubscribe from a channel.
  Receives the topic we want to unsubscribe from and the subscription channel returned by subscribe."
  [ctx topic subscription]
  (if (use-new-event-bus? ctx)
    (new/unsubscribe ctx topic subscription)
    (legacy/unsubscribe ctx topic subscription)))
