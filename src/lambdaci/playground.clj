(ns lambdaci.playground
  (:require [clojure.tools.nrepl :as repl]
   :use [clojure.pprint]))

;; Remote execution stuff

(def repl-port 64772)


(defn execute-on-slave [code]
  (with-open [conn (repl/connect :port repl-port)]
     (-> (repl/client conn 1000)    ; message receive timeout required
       (repl/message {:op "eval" :code code})
         doall)))


(defn foo [a] (println a))


(defmacro serialize
  [to-try]
  `(str (quote ~to-try)))

(execute-on-slave (serialize (clojure.pprint/pprint "bar")))
