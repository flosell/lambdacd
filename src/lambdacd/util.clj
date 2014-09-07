(ns lambdacd.util)

(defn range-from [from len] (range (inc from) (+ (inc from) len)))

(defn is-channel? [c]
  (satisfies? clojure.core.async.impl.protocols/Channel c))

(defn create-temp-dir []
  (.toString (java.nio.file.Files/createTempDirectory "foo" (into-array java.nio.file.attribute.FileAttribute []))))
