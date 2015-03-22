(ns lambdacd.dom-utils)

(defn found-in [div re]
  (let [res (.-innerHTML div)]
    (if (re-find re res)
      true
      (do (println "Not found: " res)
          false))))