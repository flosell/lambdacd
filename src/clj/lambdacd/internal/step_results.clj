(ns lambdacd.internal.step-results
  (:require [clojure.core.async :as async]
            [lambdacd.event-bus :as event-bus]))

(defn send-step-result [{step-id :step-id build-number :build-number ch :step-results-channel :as ctx } step-result]
  (let [payload {:build-number build-number :step-id step-id :step-result step-result}]
    (event-bus/publish ctx :step-result-updated payload)
    (async/>!! ch payload)))