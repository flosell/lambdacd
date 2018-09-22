(ns lambdacd.internal.default-pipeline-state-persistence
  "stores the current build history on disk"
  (:import (java.util.regex Pattern)
           (org.joda.time DateTime)
           (java.util Date))
  (:require [clojure.string :as str]
            [lambdacd.util.internal.sugar :as sugar]
            [clojure.java.io :as io]
            [clj-time.coerce :as c]
            [clojure.edn :as edn]
            [clojure.walk :as walk]
            [me.raynes.fs :as fs]
            [clojure.tools.logging :as log]))

(defn convert-if-instance [c f]
  (fn [x]
    (if (instance? c x)
      (f x)
      x)))

(defn clj-times->dates [m]
  (walk/postwalk (convert-if-instance DateTime c/to-date) m))

(defn dates->clj-times [m]
  (walk/postwalk (convert-if-instance Date c/to-date-time) m))

(defn- formatted-step-id [step-id]
  (str/join "-" step-id))

(defn- unformat-step-id [formatted-step-id]
  (map sugar/parse-int (str/split formatted-step-id (Pattern/compile "-"))))

(defn- step-result->step-result-with-formatted-step-ids [[k v]]
  {:step-id (formatted-step-id k) :step-result v})

(defn- pipeline-state->formatted-step-ids [pipeline-state]
  (map step-result->step-result-with-formatted-step-ids pipeline-state))

(defn- step-result-with-formatted-step-ids->step-result [{step-result :step-result step-id :step-id}]
  {(unformat-step-id step-id) step-result})

(defn- formatted-step-ids->pipeline-state [m]
  (into {} (map step-result-with-formatted-step-ids->step-result m)))

(defn find-build-number-in-path [path]
  (second (re-find #"build-(\d+)" (str path))))

(defn- build-state-path [dir]
  (io/file dir "build-state.edn"))

(defn- write-build-edn [path build]
  (let [serializable-build  (clj-times->dates (pipeline-state->formatted-step-ids build))
        state-as-edn-string (pr-str serializable-build)]
    (spit path state-as-edn-string)))

(defn- build-dirs [home-dir]
  (let [dir                 (io/file home-dir)
        home-contents       (file-seq dir)
        directories-in-home (filter #(.isDirectory %) home-contents)]
    directories-in-home))

(defn- build-dir [home-dir build-number]
  (let [result (str home-dir "/" "build-" build-number)]
    (.mkdirs (io/file result))
    result))

(defn write-build-history [home-dir build-number new-state]
  (if home-dir
    (let [dir      (build-dir home-dir build-number)
          edn-path (build-state-path dir)
          build    (get new-state build-number)]
      (write-build-edn edn-path build))))

(defn file-exists? [f]
  (.exists f))

(defn write-build-data-edn [home-dir build-number pipeline-data filename]
  (let [f (io/file (build-dir home-dir build-number) filename)]
    (spit f (pr-str pipeline-data))))

(defn- read-edn-file [path]
  (if (file-exists? path)
    (edn/read-string (slurp path))))

(defn- build-files [home-dir filename]
  (map #(io/file % filename) (build-dirs home-dir)))

(defn- process-build-data-file [f post-processor]
  (if-let [build-number-str (find-build-number-in-path f)]
    (post-processor (read-edn-file f) (sugar/parse-int build-number-str))
    (log/debug f "doesn't seem to contain a valid build number, skipping")))

(defn- read-and-process-data-files [home-dir filename post-processor]
  (->> (build-files home-dir filename)
       (map #(process-build-data-file % post-processor))
       (into {})))

(defn- post-process-build-state-edn [data build-number]
  (if data
    {build-number (formatted-step-ids->pipeline-state (dates->clj-times data))}))

(defn- post-process-pipeline-structure-edn [data build-number]
  {build-number (or data :fallback)})

(defn read-normal-build-data-from [home-dir filename]
  (read-and-process-data-files home-dir filename post-process-pipeline-structure-edn))

(defn read-build-state-from [home-dir]
  (read-and-process-data-files home-dir "build-state.edn" post-process-build-state-edn))

(defn clean-up-old-builds [home-dir old-build-numbers]
  (doall (map (fn [old-build-number]
                (fs/delete-dir (build-dir home-dir old-build-number))) old-build-numbers)))
