(ns lambdacd.testsupport.data
  (:require [lambdacd.util :as utils]
            [lambdacd.internal.default-pipeline-state :as default-pipeline-state]
            [clojure.core.async :as async]))


(defn- some-ctx-template []
  (let [state                (atom {})
        config {:home-dir    (utils/create-temp-dir)}
        step-results-channel (async/chan (async/dropping-buffer 100))]
    {:step-id                  [42]
     :result-channel           (async/chan (async/dropping-buffer 100))
     :step-results-channel     step-results-channel
     :pipeline-state-component nil ;; set later
     :config                   config
     :is-killed                (atom false)
     :_out-acc                 (atom "")}))

(defn- add-pipeline-state-component [template]
  (if (nil? (:pipeline-state-component template))
    (assoc template :pipeline-state-component
                    (lambdacd.internal.default-pipeline-state/new-default-pipeline-state (or (:_pipeline-state template) (atom (:initial-pipeline-state template)))
                                                                                         (:config template)
                                                                                         (:step-results-channel template)))
    template))

(defn some-ctx []
  (add-pipeline-state-component
    (some-ctx-template)))

(defn some-ctx-with [& args]
  (add-pipeline-state-component
    (apply assoc (some-ctx-template) args)))