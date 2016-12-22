(ns lambdacd.util.internal.exceptions
  (:require [clojure.repl :as repl])
  (:import (java.io StringWriter)))


(defmacro with-err-str
  [& body]
  `(let [s# (new StringWriter)]
     (binding [*err* s#]
       ~@body
       (str s#))))

(defn stacktrace-to-string [e]
  (with-err-str (repl/pst e)))
