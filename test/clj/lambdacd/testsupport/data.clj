(ns lambdacd.testsupport.data
  (:require [lambdacd.util :as utils]
            [lambdacd.event-bus :as event-bus]
            [clojure.core.async :as async]
            [lambdacd.internal.default-pipeline-state :as default-pipeline-state]
            [lambdacd.state.internal.pipeline-state-updater :as pipeline-state-updater]
            [lambdacd.core :as core]
            [lambdacd.util.internal.temp :as temp-util]))


(defn- some-ctx-template []
  (let [config {:home-dir                (temp-util/create-temp-dir)
                :ms-to-wait-for-shutdown 10000
                :step-updates-per-sec    (:step-updates-per-sec core/default-config)
                :use-new-event-bus       true}]
    (-> {:initial-pipeline-state   {} ;; only used to assemble pipeline-state, not in real life
         :step-id                  [42]
         :build-number             10
         :result-channel           (async/chan (async/dropping-buffer 100))
         :pipeline-state-component nil ;; set later
         :config                   config
         :is-killed                (atom false)
         :_out-acc                 (atom "")
         :started-steps            (atom #{})})))

(defn- add-pipeline-state-component [template]
  (if (nil? (:pipeline-state-component template))
    (assoc template :pipeline-state-component
                    (default-pipeline-state/new-default-pipeline-state (:config template) :initial-state-for-testing (:initial-pipeline-state template)))
    template))

(defn run-pipeline-state-updater [ctx]
  (if (:pipeline-state-component ctx)
    (pipeline-state-updater/start-pipeline-state-updater ctx))
  ctx)

(defn some-ctx []
  (-> (some-ctx-template)
      (add-pipeline-state-component)
      (event-bus/initialize-event-bus)
      (run-pipeline-state-updater)))

(defn some-ctx-with [& args]
  (as-> (some-ctx-template) $
        (apply assoc $ args)
        (add-pipeline-state-component $)
        (event-bus/initialize-event-bus $)
        (run-pipeline-state-updater $)))
