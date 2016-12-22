(ns lambdacd.execution.internal.util
  (:require [lambdacd.event-bus :as event-bus]))

(defn send-step-result!! [{step-id :step-id build-number :build-number :as ctx} step-result]
  (let [payload {:build-number build-number
                 :step-id      step-id
                 :step-result  step-result}]
    (event-bus/publish!! ctx :step-result-updated payload)))
