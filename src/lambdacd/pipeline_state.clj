(ns lambdacd.pipeline-state
  "responsible to manage the current state of the pipeline
  i.e. what's currently running, what are the results of each step, ..."
  (:import (java.io File))
  (:require [clojure.core.async :as async]
            [lambdacd.util :as util]
            [lambdacd.json-model :as json-model]
            [clojure.data.json :as json]
            [clojure.java.io :as io]))

(def clean-pipeline-state {})

(defn- read-state [filename]
  (let [build-number (read-string (second (re-find #"build-(\d+)" filename)))
        state (json-model/json-format->pipeline-state (json/read-str (slurp filename) :key-fn keyword))]
    { build-number state }))

(defn initial-pipeline-state [{ home-dir :home-dir }]
  (let [dir (io/file home-dir)
        home-contents (file-seq dir)
        directories-in-home (filter #(.isDirectory %) home-contents)
        build-dirs (filter #(.startsWith (.getName %) "build-") directories-in-home)
        build-state-files (map #(str % "/pipeline-state.json") build-dirs)
        states (map read-state build-state-files)]
    (into {} states)))

(defn- update-current-run [step-id step-result current-state]
  (let [current-step-result (get current-state step-id)
        new-step-result (merge current-step-result step-result)]
    (assoc current-state step-id new-step-result)))

(defn- update-pipeline-state [build-number step-id step-result current-state]
  (assoc current-state build-number (update-current-run step-id step-result (get current-state build-number))))


(defn- current-build-number-in-state [pipeline-state]
  (if-let [current-build-number (last (sort (keys pipeline-state)))]
    current-build-number
    0))

(defn current-build-number [{pipeline-state :_pipeline-state }]
  (current-build-number-in-state @pipeline-state))

(defn finished-step? [step-result]
  (let [status (:status step-result)
        is-waiting (= :waiting status)
        is-running (= :running status)]
  (not (or is-waiting is-running))))

(defn- finished-step-count-in [build]
  (let [results (vals build)
        finished-steps (filter finished-step? results)
        finished-step-count (count finished-steps)]
    finished-step-count))

(defn- call-callback-when-most-recent-build-running [callback key reference old new]
  (let [cur-build-number (current-build-number-in-state new)
        cur-build (get new cur-build-number)
        old-cur-build (get old cur-build-number)
        finished-step-count-new (finished-step-count-in cur-build)
        finished-step-count-old (finished-step-count-in old-cur-build)]
    (if (and (= 1 finished-step-count-new) (not= 1 finished-step-count-old))
      (callback))))

(defn notify-when-most-recent-build-running [{pipeline-state :_pipeline-state} callback]
  (add-watch pipeline-state :notify-most-recent-build-running (partial call-callback-when-most-recent-build-running callback)))

(defn- write-state-to-disk [home-dir build-number new-state]
  (if home-dir
  (let [dir (str home-dir "/" "build-" build-number "/")
        path (str dir "pipeline-state.json")
        state-as-json (json-model/pipeline-state->json-format (get new-state build-number))]
    (.mkdirs (io/file dir))
    (util/write-as-json path state-as-json))))

(defn update [{step-id :step-id state :_pipeline-state build-number :build-number { home-dir :home-dir } :config } step-result]
  (if (not (nil? state)) ; convenience for tests: if no state exists we just do nothing
    (let [new-state (swap! state (partial update-pipeline-state build-number step-id step-result))]
      (write-state-to-disk home-dir build-number new-state))))

(defn most-recent-step-result-with [key ctx]
  (let [state (deref (:_pipeline-state ctx))
        step-id (:step-id ctx)
        step-results (map second (reverse (sort-by first (seq state))))
        step-results-for-id (map #(get % step-id) step-results)
        step-results-with-key (filter key step-results-for-id)]
    (first step-results-with-key)))

(defn running [ctx]
  (update ctx {:status :running}))

(defn- status-for-steps [steps]
  (let [statuses (map :status (vals steps))
        has-failed (util/contains-value? :failure statuses)
        has-running (util/contains-value? :running statuses)
        has-waiting (util/contains-value? :waiting statuses)
        all-ok (every? #(= % :ok) statuses)]
    (cond
      has-failed :failure
      has-running :running
      all-ok :ok
      has-waiting :waiting
      :else :unknown)))

(defn- history-entry [[k v]]
  { :build-number k
    :status (status-for-steps v)})

(defn history-for [state]
  (map history-entry state))

(defn most-recent-build-number-in [state]
  (apply max (keys state)))
