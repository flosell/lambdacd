(ns smoketest.steps
  (:require [lambdacd.shell :as shell]
            [lambdacd.execution :as execution]
            [lambdacd.git :as git]
            [lambdacd.manualtrigger :as manualtrigger]
            [lambdacd.util :as utils]))

(defn do-stuff [& _]
  (println "foobar"))

(def some-counter (atom 0))

(def some-value-read-from-git-repo
  (atom nil))

(def some-repo-location
  (utils/create-temp-dir))
(def some-repo-uri
  (str "file://" some-repo-location))

(defn increment-counter-by-two [& _]
  (swap! some-counter #(+ 2 %1))
  {:status :success})

(defn increment-counter-by-three [& _]
  (swap! some-counter #(+ 3 %1))
  {:status :success})

(defn wait-for-some-repo [_ ctx]
  (git/wait-for-git ctx some-repo-uri "master"))

(defn ^{:display-type :container} with-some-repo [& steps]
  (git/with-git some-repo-uri steps))

(defn read-some-value-from-repo [{cwd :cwd} & _]
  (swap! some-value-read-from-git-repo (fn [_] (slurp (str cwd "/foo"))))
  {:status :success})