(ns lambdacd.pipeline-state
  "responsible to manage store the current state of the pipeline
  i.e. what's currently running, what are the results of each step, ..."
  (:require [clojure.core.async :as async]
            [lambdacd.util :as util]))

(def initial-pipeline-state {:running [] :finished []})
(def pipeline-state (atom initial-pipeline-state))

(defn get-pipeline-state []
  @pipeline-state)

(defn reset-pipeline-state []
  (reset! pipeline-state initial-pipeline-state))

(defn set-running! [step-id]
  (swap! pipeline-state #(assoc %1 :running (cons step-id (:running %1)))))

(defn update-pipeline-state [step-id step-result pipeline-state]
  (let [cur-finished (:finished pipeline-state)
        new-finished (cons step-id cur-finished)
        cur-running  (:running pipeline-state)
        new-running  (remove (partial = step-id) cur-running)
        cur-results  (:results pipeline-state)
        new-results  (assoc cur-results step-id step-result)]
  {:finished new-finished
   :running new-running
   :results new-results}))

(defn set-finished! [step-id step-result] ;; TODO: this should also remove it from the running-list. at the moment, css magic makes appear ok
  (swap! pipeline-state (partial update-pipeline-state step-id step-result)))

