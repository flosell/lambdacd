(ns lambdacd.util.internal.coll)

(defn fill [coll length filler]
  (let [missing (- length (count coll))]
    (concat coll (replicate missing filler))))
