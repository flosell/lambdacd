(ns lambdacd.util.internal.temp
  (:require [clojure.java.io :as io]
            [me.raynes.fs :as fs])
  (:import (java.nio.file.attribute FileAttribute)
           (java.nio.file Files LinkOption)))

(defn- no-file-attributes []
  (into-array FileAttribute []))


(def temp-prefix "lambdacd")

(defn create-temp-dir
  ([]
   (str (Files/createTempDirectory temp-prefix (no-file-attributes))))
  ([parent]
   (str (Files/createTempDirectory (.toPath (io/file parent)) temp-prefix (into-array FileAttribute [])))))


(defn create-temp-file []
  (str (Files/createTempFile temp-prefix "" (no-file-attributes))))

(defmacro with-temp
  "evaluates the body, then deletes the given file or directory.
  returns the result of the evaluation of the body"
  [f & body]
  `(try
     ~@body
     (finally
       (fs/delete-dir ~f LinkOption/NOFOLLOW_LINKS))))
