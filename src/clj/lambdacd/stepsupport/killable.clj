(ns lambdacd.stepsupport.killable
  "Functions that help in adding the ability for a step to be killed."
  (:require [clojure.core.async :as async]))

(defn killed?
  "Returns true if the step was killed."
  [ctx]
  @(:is-killed ctx))

(defmacro if-not-killed
  "Executes the given body unless the step was killed. If killed, sets the status accordingly.

  Example:
  ```clojure
  (loop []
    (if-not-killed ctx
                   (if (should-stop-waiting?)
                     {:status :success}
                     (recur))))
  ```"
  [ctx & body]
  `(if (killed? ~ctx)
     (do
       (async/>!! (:result-channel ~ctx) [:status :killed])
       {:status :killed})
     ~@body))
