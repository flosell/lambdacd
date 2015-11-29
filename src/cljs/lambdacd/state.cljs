(ns lambdacd.state)


(defn flatten-state [steps]
  (flatten (map #(tree-seq associative? :children %) steps)))

(defn- is-step [step-id step]
  (let [step-step-id (:step-id step)
        found (= step-step-id step-id)]
  found))

(defn find-by-step-id [steps step-id]
  (first (filter #(is-step step-id %) (flatten-state steps))))

(defn- is-active? [step]
  (let [status (get-in step [:result :status])]
    (or
      (= "running" status)
      (= "waiting" status))))
