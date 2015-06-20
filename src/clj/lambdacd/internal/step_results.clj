(ns lambdacd.internal.step-results
  (:require [clojure.core.async :as async]))

(defn send-step-result [{step-id :step-id build-number :build-number ch :step-results-channel } step-result]
  (async/>!! ch  {:build-number build-number :step-id step-id :step-result step-result}))