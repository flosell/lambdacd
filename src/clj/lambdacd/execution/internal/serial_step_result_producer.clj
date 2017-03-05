(ns lambdacd.execution.internal.serial-step-result-producer
  (:require [lambdacd.execution.internal.execute-step :as execute-step]
            [lambdacd.execution.internal.util :as execution-util]))

(defn- keep-original-args [old-args step-result]
  (merge old-args step-result))

(defn args-for-subsequent-step [parent-step-args current-step-args execute-step-result]
  (->> (first (vals (:outputs execute-step-result)))
       (execution-util/keep-globals current-step-args)
       (keep-original-args parent-step-args)))

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
              new-result   (cons step-result result)
              new-args     (args-for-subsequent-step args cur-args step-result)]
          (if (stop-predicate step-result)
            new-result
            (recur (cons step-result result) (rest remaining-steps-with-id) new-args)))))))
