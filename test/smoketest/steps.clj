(ns smoketest.steps
  (:require [lambdacd.shell :as shell]
            [lambdacd.execution :as execution]
            [lambdacd.git :as git]
            [lambdacd.manualtrigger :as manualtrigger]))

(defn do-stuff [& _]
  (println "foobar"))

(def some-counter (atom 0))

(defn increment-counter-by-two [& _]
  (swap! some-counter #(+ 2 %1) ))

(defn increment-counter-by-three [& _]
  (swap! some-counter #(+ 3 %1)))