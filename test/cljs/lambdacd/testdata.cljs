(ns lambdacd.testdata)

(def some-build-step
  {:name "some-step"
   :type "step"
   :step-id [1 2 3]
   :children []
   :result {:status "success"
            :out "hello world"}})

(defn with-name [step name]
  (assoc step :name name))

(defn with-step-id [step step-id]
  (assoc step :step-id step-id))

(defn with-type [step name]
  (assoc step :type name))

(defn with-output [step output]
  (assoc step :result {:status "success" :out output}))

(defn with-children [step children]
  (assoc step :children children))