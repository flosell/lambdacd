(ns lambdacd.execution.internal.util
  (:require [lambdacd.event-bus :as event-bus]
            [lambdacd.steps.result :as step-results]))

(defn send-step-result!! [{step-id :step-id build-number :build-number :as ctx} step-result]
  (let [payload {:build-number build-number
                 :step-id      step-id
                 :step-result  step-result}]
    (event-bus/publish!! ctx :step-result-updated payload)))

(defn not-success? [step-result]
  (not= :success (:status step-result)))

(defn keep-globals [old-args step-result] ; TODO: is this the right place for this function?
  (let [existing-globals              (:global old-args)
        new-globals                   (:global step-result)
        merged-globals                (merge existing-globals new-globals)
        args-with-old-and-new-globals (assoc step-result :global merged-globals)]
    args-with-old-and-new-globals))

(defn merge-two-step-results [r1 r2]
  (step-results/merge-two-step-results r1 r2 :resolvers [step-results/status-resolver
                                                         step-results/merge-nested-maps-resolver
                                                         step-results/combine-to-list-resolver
                                                         step-results/second-wins-resolver]))
