(ns lambdacd.testsupport.matchers)

(defn map-containing [expected m]
  (and (every? (set (keys m)) (keys expected))
       (every? #(= (m %)(expected %)) (keys expected))))
