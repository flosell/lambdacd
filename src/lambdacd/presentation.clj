(ns lambdacd.presentation
  "this namespace is responsible for converting the pipeline
  into a nice, map-format that we can use to display the pipeline
  in a UI")


(defn- is-fn? [fun]
  (not (or (string? fun) (map? fun)))) ; hackedyhack...

(defn- display-type [fun]
  (if (symbol? fun)
    (let [metadata-dt (:display-type (meta (find-var fun)))]
    (if (nil? metadata-dt)
      (if (is-fn? fun)
        :step
        :unknown)
      metadata-dt))
    (if (sequential? fun)
      :step
      :unknown)))

(defn- is-step? [step]
  (= :step (display-type step)))

; hacky?
(defn- clear-namespace [s]
  (clojure.string/replace s #"[^/]+/" ""))

(defn- display-name [fun]
  (clear-namespace (str fun)))

(declare display-representation) ; display-representation and display-representation-for-seq are mutually recursive

(defn- do-some-stuff [part id]
  (map-indexed #(display-representation %2 (conj id (inc %1))) part))

(defn- display-representation-for-seq [part id]
  (let [f (first part)
          r (filter is-step? (rest part))]
    (if (is-step? f)
      (do-some-stuff part id)
      {:name (display-name f)
       :type (display-type f)
       :step-id id
       :children (do-some-stuff r id)})))

(defn display-representation
  ([part]
   (display-representation part '()))
  ([part id]
  (if (sequential? part)
    (display-representation-for-seq part id)
    {:name (display-name part)
     :type (display-type part)
     :step-id id})))
