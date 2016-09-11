(ns lambdacd.internal.default-pipeline-state-persistence
  "stores the current build history on disk"
  (:import (java.util.regex Pattern)
           (java.io File)
           (org.joda.time DateTime)
           (java.util Date))
  (:require [clojure.string :as str]
            [lambdacd.util :as util]
            [clojure.java.io :as io]
            [clj-time.coerce :as c]
            [clojure.edn :as edn]
            [clojure.walk :as walk]
            [clojure.data :as data]
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
  (map util/parse-int (str/split formatted-step-id (Pattern/compile "-"))))

(defn- step-result->step-result-with-formatted-step-ids [[k v]]
  {:step-id (formatted-step-id k) :step-result v})

(defn- pipeline-state->formatted-step-ids [pipeline-state]
  (map step-result->step-result-with-formatted-step-ids pipeline-state))

(defn- step-result-with-formatted-step-ids->step-result [{step-result :step-result step-id :step-id}]
  {(unformat-step-id step-id) step-result})

(defn- formatted-step-ids->pipeline-state [m]
  (into {} (map step-result-with-formatted-step-ids->step-result m)))

(defn- build-number-from-path [path]
  (util/parse-int (second (re-find #"build-(\d+)" path))))

(defn- build-state-path [dir]
  (str dir "/" "build-state.edn"))

(defn- read-build-edn [dir]
  (let [path         (build-state-path dir)
        build-number (build-number-from-path path)
        data-str     (slurp path)
        state        (formatted-step-ids->pipeline-state (dates->clj-times (edn/read-string data-str)))]
    {build-number state}))

(defn- write-build-edn [path build]
  (let [serializable-build  (clj-times->dates (pipeline-state->formatted-step-ids build))
        state-as-edn-string (pr-str serializable-build)]
    (spit path state-as-edn-string)))

(defn- build-dirs [home-dir]
  (let [dir                 (io/file home-dir)
        home-contents       (file-seq dir)
        directories-in-home (filter #(.isDirectory %) home-contents)
        build-dirs          (filter #(.startsWith (.getName %) "build-") directories-in-home)]
    build-dirs))

(defn write-build-history [home-dir build-number new-state]
  (if home-dir
    (let [dir   (str home-dir "/" "build-" build-number)
          edn-path  (build-state-path dir)
          build (get new-state build-number)]
      (.mkdirs (io/file dir))
      (write-build-edn edn-path build))))

(defn read-build-history-from [home-dir]
  (let [states              (map #(read-build-edn %) (build-dirs home-dir))]
    (into {} states)))

(defn clean-up-old-history [home-dir new-state]
  (if home-dir
    (let [existing-build-dirs    (map str (build-dirs home-dir))
          expected-build-numbers (keys new-state)
          expected-build-dirs    (map #(str (io/file home-dir (str "build-" %))) expected-build-numbers)
          [only-in-existing _ _] (data/diff (set existing-build-dirs) (set expected-build-dirs))]
      (doall (for [old-build-dir only-in-existing]
               (do
                 (log/info "Cleaning up old build directory" old-build-dir)
                 (fs/delete-dir old-build-dir)))))))
