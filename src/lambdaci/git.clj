(ns lambdaci.git
  (:require [lambdaci.shell :as sh]))

(defn current-revision [repo-uri branch]
  (.trim (:out (sh/bash "/" (str "git ls-remote --heads " repo-uri " " branch " | cut -f 1")))))

(defn wait-for-git [repo-uri branch]
  (loop [last-seen-revision (current-revision repo-uri branch)]
    (println "waiting for git-commit, last seen revision:" last-seen-revision)
    (let [revision-now (current-revision repo-uri branch)]
      (if (not= last-seen-revision revision-now)
        (do
          {:status :success :revision revision-now})
        (do
          (Thread/sleep 1000)
          (recur revision-now))
        ))))
