(ns lambdacd.stepstatus.predicates
  "Predicates over step status.")

(defn is-active?
  "Returns true if the status indicates that the step is currently doing something as opposed to being finished."
  [status]
  (contains? #{:running :waiting} status))
