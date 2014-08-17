(ns lambdaci.dsl)

(defn- range-from [from len] (range (inc from) (+ (inc from) len)))

(defn execute-step [step args step-id]
  (let [step-result (step args step-id)]
    (println (str "executing step " step-id step-result))
    {:outputs { step-id step-result}} ))


(defn steps-with-ids [steps prev-id]
  (let [significant-part (first prev-id)
        rest-part (rest prev-id)
        significant-ids (range-from significant-part (count steps))
        ids (map #(cons %1 rest-part) significant-ids)]
    (map vector ids steps)))

(defn merge-step-results [r1 r2]
  (merge-with merge r1 r2))

(defn execute-step-and-merge [args [step-id step]]
  (let [step-result (execute-step step args step-id)]
    (merge-step-results args step-result)))


(defn execute-steps [steps args step-id]
  (reduce execute-step-and-merge args (steps-with-ids steps step-id)))

(defn in-parallel [& steps]
  (fn [args step-id]
    (execute-steps steps args (cons 0 step-id)))) ; FIXME: this isn't real parallelism!

(defn in-cwd [cwd & steps]
  (fn [args step-id]
    (execute-steps steps (assoc args :cwd cwd) (cons 0 step-id))))


(defn run [pipeline]
  (let [runnable-pipeline (map eval pipeline)]
    (execute-steps runnable-pipeline {} [0])))
