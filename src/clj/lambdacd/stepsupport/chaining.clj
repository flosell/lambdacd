(ns lambdacd.stepsupport.chaining
  "Functions that allow you to use LambdaCDs pipeline mechanisms inside a step, i.e. chain several steps together:

  ```clojure
  (defn some-step [args ctx]
    (chaining args ctx
              (shell/bash injected-ctx \"/ \" \"echo hello \")
              (shell/bash injected-ctx \"/ \" \"echo world \")))

  (defn some-step-with-error-handling [args ctx]
    (always-chaining args ctx
                     (run-tests injected-args injected-ctx)
                     (if (= :failure (:status injected-args))
                       (publish-test-failures injected-args injected-ctx))))
  ```"
  (:require [clojure.walk :as walk]
            [lambdacd.stepresults.merge-resolvers :as merge-resolvers]
            [lambdacd.stepresults.merge :as merge]
            [lambdacd.step-id :as step-id]
            [lambdacd.execution.internal.execute-steps :as execute-steps]
            [lambdacd.execution.internal.serial-step-result-producer :as serial-step-result-producer]))

(defn- merge-step-results-with-joined-output [a b]
  (merge/merge-two-step-results a b :resolvers [merge-resolvers/status-resolver
                                                merge-resolvers/merge-nested-maps-resolver
                                                merge-resolvers/join-output-resolver
                                                merge-resolvers/second-wins-resolver]))

(defn- wrap-step-to-allow-nil-values [step]
  (fn [args ctx]
    (let [result (step args ctx)]
      (if (nil? result)
        args
        result))))

(defn- step-results-sorted-by-id [outputs]
  (->> outputs
       (into (sorted-map-by step-id/before?))
       (vals)))

(defn- unify-results [step-results]
  (-> step-results
      (step-results-sorted-by-id)
      (merge/merge-step-results merge-step-results-with-joined-output)))

(defn- do-chain-steps-with-execute-steps [args ctx steps step-result-producer]
  (let [robust-steps (map wrap-step-to-allow-nil-values steps)
        execute-step-result (execute-steps/execute-steps robust-steps args ctx
                                                         :step-result-producer step-result-producer
                                                         :unify-results-fn unify-results)]
    (merge (unify-results (:outputs execute-step-result)) execute-step-result)))

(defn chain-steps
  "Takes a number of steps executes them as if they were part of the pipeline, i.e. will execute them one after the other, stop on failures
  and call the next step with the the results of the previous step as `args`.
  Think of this as an opinionated take on `execute-steps` so for full flexibility, use `execute-steps` directly.

  Look at the `chaining`-macro for a more flexible syntax.

  Example:
  ```clojure
  (chain-steps (some-args) (some-ctx)
               some-step
               some-other-step)
  ```"
  [args ctx & steps]
  (do-chain-steps-with-execute-steps args ctx
                                     steps
                                     (serial-step-result-producer/serial-step-result-producer)))

(defn always-chain-steps
  "Like `chain-steps` but does not stop after failure."
  [args ctx & steps]
  (do-chain-steps-with-execute-steps args ctx
                                     steps
                                     (serial-step-result-producer/serial-step-result-producer :stop-predicate (constantly false))))

;; Placeholders where args and ctx are injected by the chaining-macro.
(def ^{:doc        "Placeholder for `args` for use in `chaining` and `always-chaining`"
       :deprecated "0.13.1"} injected-args)
(def ^{:doc "Placeholder for `ctx` for use in `chaining` and `always-chaining`"} injected-ctx)

(defn- replace-args-and-ctx [args ctx]
  (fn [x]
    (cond
      (and
        (symbol? x)
        (= (var injected-args) (resolve x))) args
      (and
        (symbol? x)
        (= (var injected-ctx) (resolve x))) ctx
      :else x)))

(defn- to-fn-with-args [form]
  (let [f#   (first form)
        ctx  (gensym "ctx")
        args (gensym "args")
        r#   (walk/postwalk
               (replace-args-and-ctx args ctx)
               (next form))]
    (if (map? form)
      `(fn [& _#] ~form)
      `(fn [~args ~ctx] (~f# ~@r#)))))

(defn- do-chaining [chain-fn args ctx forms]
  (let [fns (vec (map to-fn-with-args forms))]
    `(apply ~chain-fn ~args ~ctx ~fns)))

(defmacro chaining
  "Syntactic sugar for `chain-steps`. Can work with arbitrary code and can inject `args` and `ctx`.

  Example:
  ```clojure
  (chaining (some-args) (some-ctx)
            (some-step injected-args injected-ctx)
            (some-other-step injected-args injected-ctx))
  ```"
  [args ctx & forms]
  (do-chaining chain-steps args ctx forms))

(defmacro always-chaining
  "Like `chaining` but does not stop on failures. Status of the chain will be failure if one of the steps in the chain failed. Use `last-step-status-wins` to make the chain succeed even though a step in the chain failed."
  [args ctx & forms]
  (do-chaining always-chain-steps args ctx forms))

(defn last-step-status-wins
  "Takes a step result from chaining or execute-steps and replaces its status with the status of the last child.
  Often used together with always-chaining to be able to control the chains end-result after an element of the chain failed (see [#122](https://github.com/flosell/lambdacd/issues/122) for details).

  Example:
  ```clojure
  (last-step-status-wins
    (always-chaining args ctx
                     (check-something injected-args injected-ctx)
                     (if (= :failure (:status injected-args))
                       (manualtrigger/wait-for-manual-trigger injected-args injected-ctx)
                       {:status :success})))
  ```"
  [step-result]
  (let [winning-status (->> step-result
                            :outputs
                            (sort-by #(vec (first %)))
                            last
                            second
                            :status)]
    (assoc step-result :status winning-status)))
