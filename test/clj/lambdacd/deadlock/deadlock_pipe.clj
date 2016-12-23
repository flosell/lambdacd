(ns lambdacd.deadlock.deadlock-pipe
  (:use [clojure.test])
  (:require [lambdacd.steps.support :as support]
            [lambdacd.steps.control-flow :refer [in-parallel]]
            [lambdacd.execution :as exec]
            [lambdacd.util :as util]
            [clojure.core.async :as as]))

(def bacon-ipsum "Bacon ipsum dolor amet meatloaf tongue flank biltong ground round. Sirloin venison sausage kielbasa andouille strip steak tri-tip. Meatloaf biltong capicola shoulder. Sausage shankle pancetta prosciutto porchetta short loin.\n\n")

(defn print-1000-lines-in-clj [args ctx]
  (let [out-ch (:result-channel ctx)]
    (support/capture-output ctx
                            (doseq [i (range 1 1000)]
                              (do
                                (println i ")" bacon-ipsum)
                                )))
    {:status :success}))

(defn done-step [args ctx]
  (println "Done")
  {:status :success}
  )

(def pipeline-def
  `(
     (in-parallel
       print-1000-lines-in-clj
       print-1000-lines-in-clj
       print-1000-lines-in-clj
       print-1000-lines-in-clj
       print-1000-lines-in-clj

       print-1000-lines-in-clj
       print-1000-lines-in-clj
       print-1000-lines-in-clj
       print-1000-lines-in-clj
       print-1000-lines-in-clj

       print-1000-lines-in-clj
       print-1000-lines-in-clj
       print-1000-lines-in-clj
       print-1000-lines-in-clj
       print-1000-lines-in-clj

       print-1000-lines-in-clj
       print-1000-lines-in-clj
       print-1000-lines-in-clj
       print-1000-lines-in-clj
       print-1000-lines-in-clj

       print-1000-lines-in-clj
       print-1000-lines-in-clj
       print-1000-lines-in-clj
       print-1000-lines-in-clj
       print-1000-lines-in-clj

       print-1000-lines-in-clj
       print-1000-lines-in-clj
       print-1000-lines-in-clj
       print-1000-lines-in-clj
       print-1000-lines-in-clj

       print-1000-lines-in-clj
       print-1000-lines-in-clj
       print-1000-lines-in-clj
       print-1000-lines-in-clj
       print-1000-lines-in-clj

       print-1000-lines-in-clj
       print-1000-lines-in-clj
       print-1000-lines-in-clj
       print-1000-lines-in-clj
       print-1000-lines-in-clj)
     done-step
     )

  )

(deftest deadlock-test
  (testing "should not end in a deadlock"
    (let [home-dir (util/create-temp-dir)
          config {:home-dir             home-dir
                  :name                 "deadlock pipeline"
                  :step-updates-per-sec 10}
          pipeline (lambdacd.core/assemble-pipeline pipeline-def config)
          sub (lambdacd.event-bus/subscribe (:context pipeline) :step-finished)
          count-finished (atom 0)
          ]
      (println @count-finished)
      (as/go-loop []
        (as/<! sub)
        (swap! count-finished inc)
        (recur))

      (exec/run (:pipeline-def pipeline) (:context pipeline))

      (println @count-finished)

      ))

  )