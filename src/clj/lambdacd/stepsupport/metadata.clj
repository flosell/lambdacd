(ns lambdacd.stepsupport.metadata
  "Functions that support build steps in dealing with metadata")

(defn assoc-build-metadata!
  "Like `assoc` but for the build-metadata. Adds or replaces key-value pairs in the build metadata map."
  [ctx & kvs]
  (swap! (:build-metadata-atom ctx) #(apply assoc % kvs)))
