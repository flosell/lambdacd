(ns lambdacd.stress-test
  (:require [clojure.test :refer :all]
            [lambdacd.execution.core :as execution]
            [clojure.core.async :as async]
            [lambdacd.steps.control-flow :refer [in-parallel]]
            [lambdacd.util.internal.temp :as temp-utils]
            [lambdacd.steps.support :as support]
            [lambdacd.util :as util]))


(def bacon-ipsum "Bacon ipsum dolor amet meatloaf tongue flank biltong ground round. Sirloin venison sausage kielbasa andouille strip steak tri-tip. Meatloaf biltong capicola shoulder. Sausage shankle pancetta prosciutto porchetta short loin.\n\n")

(defn print-1000-lines-in-clj [args ctx]
  (support/capture-output ctx
    (doseq [i (range 1 1000)]
      (do
        (println i ")" bacon-ipsum))))
  {:status :success})

(defn done-step [args ctx]
  {:status :success})

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
     done-step))

(deftest ^:smoke deadlock-test ; From #143, reproduces deadlock also described in #144
  (testing "should not end in a deadlock"
    (let [home-dir (temp-utils/create-temp-dir)
          config   {:home-dir             home-dir
                    :name                 "deadlock pipeline"
                    :step-updates-per-sec 10
                    :use-new-event-bus    true
                    :shutdown-sequence    (fn [& _ ] (println "Shutting down deadlock-test pipeline, doing nothing"))}
          pipeline (lambdacd.core/assemble-pipeline pipeline-def config)
          sub (lambdacd.event-bus/subscribe (:context pipeline) :step-finished)
          count-finished (atom 0)]

      (async/go-loop []
        (async/<! sub)
        (swap! count-finished inc)
        (recur))

      (is (not= :timeout
                  (deref (future
                           (execution/run-pipeline (:pipeline-def pipeline) (:context pipeline)))
                         300000 :timeout)))

      (is (= 42 @count-finished)))))
