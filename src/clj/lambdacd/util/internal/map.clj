(ns lambdacd.util.internal.map)

(defn put-if-not-present [m k v]
  (if (contains? m k)
    m
    (assoc m k v)))

(defn contains-value? [v coll]
  (some #(= % v) coll))

(defn merge-with-k-v [f & maps]
  (when (some identity maps)
    (let [merge-entry (fn [m e]
                        (let [k (key e) v (val e)]
                          (if (contains? m k)
                            (assoc m k (f k (get m k) v))
                            (assoc m k v))))
          merge2 (fn [m1 m2]
                   (reduce merge-entry (or m1 {}) (seq m2)))]
      (reduce merge2 maps))))
