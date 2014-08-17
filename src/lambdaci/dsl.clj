(ns lambdaci.dsl)

(def initial-pipeline-state {:running [] :finished []})
(def pipeline-state (atom initial-pipeline-state))

(defn get-pipeline-state []
  @pipeline-state)

(defn reset-pipeline-state []
  (reset! pipeline-state initial-pipeline-state))

(defn set-running! [step-id]
  (swap! pipeline-state #(assoc %1 :running (cons step-id (:running %1)))))

(defn set-finished! [step-id] ;; TODO: this should also remove it from the running-list. at the moment, css magic makes appear ok
  (swap! pipeline-state #(assoc %1 :finished (cons step-id (:finished %1)))))

(defn- range-from [from len] (range (inc from) (+ (inc from) len)))

(defn execute-step [step args step-id]
  (set-running! step-id)
  (let [step-result (step args step-id)]
    (println (str "executing step " step-id step-result))
    (set-finished! step-id)
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
  (reset-pipeline-state)
  (let [runnable-pipeline (map eval pipeline)]
    (execute-steps runnable-pipeline {} [0])))
