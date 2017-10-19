(ns lambdacd.util.internal.reflection)

(defn private-field [obj fn-name-string]
  (let [m (.. obj getClass (getDeclaredField fn-name-string))]
    (.setAccessible m true)
    (.get m obj)))
