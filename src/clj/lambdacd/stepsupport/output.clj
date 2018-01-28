(ns lambdacd.stepsupport.output
  "Functions and macros that simplify dealing with a steps user readable output (`:out`). Two approaches are provided:
  The _printer_ approach that gives full control over what will be provided as output and the `capture-output`-approach that just redirects all stdout."
  (:require [clojure.core.async :as async])
  (:import (java.io Writer StringWriter)))

(defn set-output
  "Reset the steps output to the given value."
  [ctx msg]
  (async/>!! (:result-channel ctx) [:out msg]))

(defn- append-output [msg]
  (fn [old-output]
    (str old-output msg "\n")))

; ------- PRINTER -------

(defn new-printer
  "Returns a datastructure to collect output (to be used with `print-to-output` and `printed-output`).

  Example:
  ```clojure
  > (let [printer (new-printer)]
      (print-to-output ctx printer \"Hello\")
      (print-to-output ctx printer \"World\")
      (printed-output printer))
  \"Hello\\nWorld\\n\"
  ```"
  []
  (atom ""))


(defn print-to-output
  "Appends the steps output with the given message (see `new-printer` for an example)"
  [ctx printer msg]
  (let [new-out (swap! printer (append-output msg))]
    (set-output ctx new-out)))

(defn printed-output
  "Get the output accumulated in previous `print-to-output` calls (see `new-printer` for an example)"
  [printer]
  @printer)

; ------- END PRINTER -------

(defn ^:no-doc writer-to-ctx
; not part of the public interface, just public for the macro
  [ctx]
  (let [buf (StringWriter.)]
    {:writer (proxy [Writer] []
               (write [& [x ^Integer off ^Integer len]]
                 (cond
                   (number? x) (.append buf (char x))
                   (not off) (.append buf x)
                   ; the CharSequence overload of append takes an *end* idx, not length!
                   (instance? CharSequence x) (.append buf ^CharSequence x (int off) (int (+ len off)))
                   :else (do
                           (.append buf (String. ^chars x) off len))))
               (flush []
                 (set-output ctx (.toString (.getBuffer buf)))))
     :buffer (.getBuffer buf)}))

(defmacro capture-output
  "Redirect build steps stdout to its `:out` channel by rebinding clojure-stdout.
  If the result of the given body is a map (like a step-result), it automatically prepends the collected stdout to `:out`.
  Example:
  ```clojure
  > (capture-output (some-ctx)
                    (println \"Hello\")
                    (println \"World\")
                    {:status :success
                     :out \"From Step\"})
  {:status :success, :out \"Hello\\nWorld\\n\\nFrom Step\"}
  ```
  "
  [ctx & body]
  `(let [{x#      :writer
          buffer# :buffer} (writer-to-ctx ~ctx)
         body-result# (binding [*out* x#]
                        (do ~@body))]
     (if (associative? body-result#)
       (update body-result# :out #(if (nil? %) (str buffer#) (str buffer# "\n" % ))))))
