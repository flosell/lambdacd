(ns lambdacd.stepresults.flatten
  "Functions to convert a nested step result and transform it into a flat list")

(defn flatten-step-result-outputs
  "Takes a nested step-result map (like those returned by a step or sent in a:pipeline-finished event)
  and converts it into a flat map of step results where every step is accessible with its step-id:
  ```
  > (flatten-step-result-outputs {[1] {:status :success}
                                  [2] {:status  :success
                                       :outputs {[1 2] {:status :success :step [1 2]}}}})
  {[1]   {:status :success}
   [2]   {:status  :success
          :outputs {[1 2] {:status :success :step [1 2]}}}
   [1 2] {:status :success :step [1 2]}}
  ```"
  [outputs]
  (into {}
        (for [[k v] outputs]
          (if (:outputs v)
            (assoc (flatten-step-result-outputs (:outputs v)) k v)
            {k v}))))
