(ns lambdacd.smoketest.steps
  (:require [lambdacd.steps.git :as git]
            [lambdacd.util :as utils]))

(defn do-stuff [& _]
  (println "foobar"))

(def some-counter (atom 0))

(def some-value-read-from-git-repo
  (atom nil))

(def the-global-value (atom nil))

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
  (let [the-value (swap! some-value-read-from-git-repo (fn [_] (slurp (str cwd "/foo"))))]
    {:status :success :global {:value-from-repo the-value}}))

(defn use-global-value [{{v :value-from-repo} :global} & _]
  (let [hello-global (str "hello " v)]
  (swap! the-global-value (constantly hello-global))
  {:status :success}))
