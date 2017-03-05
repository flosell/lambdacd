(ns lambdacd.stepresults.flatten)

(defn flatten-step-result-outputs [outputs]
  (into {}
        (for [[k v] outputs]
          (if (:outputs v)
            (assoc (flatten-step-result-outputs (:outputs v)) k v)
            {k v}))))
