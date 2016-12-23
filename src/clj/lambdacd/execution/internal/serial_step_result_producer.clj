(ns lambdacd.execution.internal.serial-step-result-producer
  (:require [lambdacd.execution.internal.execute-step :as execute-step]
            [lambdacd.execution.internal.util :as execution-util]))

(defn- keep-original-args [old-args step-result]
  (merge old-args step-result))

(defn serial-step-result-producer [& {:keys [stop-predicate] ; TODO: should this be in a public namespace? 
                                      :or   {stop-predicate execution-util/not-success?}}]
  (fn [args s-with-id]
    (loop [result                  ()
           remaining-steps-with-id s-with-id
           cur-args                args]
      (if (empty? remaining-steps-with-id)
        result
        (let [ctx-and-step (first remaining-steps-with-id)
              step-result  (execute-step/execute-step cur-args ctx-and-step)
              step-output  (first (vals (:outputs step-result)))
              new-result   (cons step-result result)
              new-args     (->> step-output
                                (execution-util/keep-globals cur-args)
                                (keep-original-args args))]
          (if (stop-predicate step-result)
            new-result
            (recur (cons step-result result) (rest remaining-steps-with-id) new-args)))))))
