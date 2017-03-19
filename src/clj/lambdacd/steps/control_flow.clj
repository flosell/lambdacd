(ns lambdacd.steps.control-flow
  "Control-flow elements for a pipeline: steps that control the way their child-steps are being run."
  (:require [lambdacd.execution.core :as execution]
            [clojure.core.async :as async]
            [lambdacd.steps.support :as support]
            [lambdacd.step-id :as step-id]

            [lambdacd.util.internal.temp :as temp-util]
            [lambdacd.execution.internal.serial-step-result-producer :as serial-step-result-producer]
            [lambdacd.stepstatus.unify :as unify])
  (:refer-clojure :exclude [alias])
  (:import (java.util UUID)))

(defn- post-process-container-results [result]
  (let [outputs (vals (:outputs result))
        globals (support/merge-globals outputs)
        merged-step-results (support/merge-step-results outputs)]
    (merge merged-step-results result {:global globals})))

(defn- wait-for-finished-on [step-result-chs]
  (let [all-resp-results-ch    (async/merge step-result-chs)
        unified-status         (atom :unknown)
        successful-step-result (async/<!!
                                 (async/go-loop [statuses []]
                                   (if-let [result (async/<! all-resp-results-ch)]
                                     (let [new-statuses (conj statuses (:status result))]
                                       (reset! unified-status (unify/successful-when-one-successful new-statuses))
                                       (if (= :success (:status result))
                                         result
                                         (recur new-statuses))))))]
    (if (nil? successful-step-result)
      [{:status @unified-status}]
      [successful-step-result])))

(defn- either-step-result-producer [args steps-and-ids]
  (let [step-result-chs (map #(async/thread (execution/execute-step args %)) steps-and-ids)]
    (wait-for-finished-on step-result-chs)))

(defn synchronize-atoms
  "Deprecated, will be private or moved in the future."
  {:deprecated "0.13.1"}
  [source target]
  (let [key (UUID/randomUUID)]
    (add-watch source key #(reset! target %4))
    key))

(defn ^{:display-type :parallel} either
  "Build step that executes its children in parallel and returns after the first child finished successfully.
  Commonly used to wait for one of multiple triggers to return.

  Example:
  ```clojure
  (def pipeline-structure
    `((either
        wait-for-git-commit
        wait-for-manual-trigger)
      build
      test
      deploy))
  ```
  "
  [& steps]
  (fn [args ctx]
    (let [parent-kill-switch (:is-killed ctx)
          kill-switch        (atom false)
          watch-ref          (synchronize-atoms parent-kill-switch kill-switch)
          _                  (reset! kill-switch @parent-kill-switch)
          execute-output     (execution/execute-steps steps args ctx
                                                      :is-killed kill-switch
                                                      :step-result-producer either-step-result-producer
                                                      :retrigger-predicate (constantly :rerun)
                                                      :unify-results-fn (support/unify-only-status unify/successful-when-one-successful))]
      (reset! kill-switch true)
      (remove-watch parent-kill-switch watch-ref)
      (if (= :success (:status execute-output))
        (first (vals (:outputs execute-output)))
        execute-output))))

(defn- parallel-retrigger-predicate [ctx step]
  (let [cur-step-id (:step-id ctx)
        retriggered-step-id (:retriggered-step-id ctx)]
    (if (or
          (step-id/parent-of? cur-step-id retriggered-step-id)
          (step-id/parent-of? retriggered-step-id cur-step-id)
          (= cur-step-id retriggered-step-id))
      :rerun
      :mock)))

(defn- parallel-step-result-producer [args steps-and-ids]
  (pmap #(execution/execute-step args %) steps-and-ids))

(defn- execute-steps-in-parallel [steps args ctx]
  (execution/execute-steps steps args ctx
                           :step-result-producer parallel-step-result-producer
                           :unify-results-fn (support/unify-only-status unify/successful-when-all-successful)
                           :retrigger-predicate parallel-retrigger-predicate
                           :is-killed (:is-killed ctx)))

(defn ^{:display-type :parallel} in-parallel
  "Build step that executes its children in parallel and returns after all children finished successfully.
  Commonly used to parallelize build independent build steps

  Example:
  ```clojure
  (def pipeline-structure
    `(; ...
      build
      (in-parallel
        test-backend
        test-frontend)
      deploy))
  ```
  "
  [& steps]
  (fn [args ctx]
    (post-process-container-results
      (execute-steps-in-parallel steps args ctx))))


(defn ^{:display-type :container} in-cwd
  "Build step that executes its children in sequence and passes the value of a given working directory to each of them.
  Returns once the last step finished.

  Example:
  ```clojure
  (defn some-step [args ctx]
    (println \"The working directory:\" (:cwd args)))

  (def pipeline-structure
    `(; ...
      (in-cwd \"/tmp/some-dir\"
        some-step
        some-other-step)))
  ```
  "
  [cwd & steps]
  (fn [args ctx]
    (post-process-container-results
      (execution/execute-steps steps (assoc args :cwd cwd) ctx
                               :unify-results-fn (support/unify-only-status unify/successful-when-all-successful)))))

(defn- run-steps-in-sequence [args ctx steps]
  (post-process-container-results
    (execution/execute-steps steps args ctx
                             :unify-results-fn (support/unify-only-status unify/successful-when-all-successful)
                             :is-killed (:is-killed ctx))))


(defn ^{:display-type :container} run
  "Build step that executes its children in sequence and returns once the last step finished.
  Commonly used to pass a chain of steps to into a control flow expecting only one (e.g. `either`, `junction`) or
  to wrap related steps into a container and `alias` it to structure the pipeline.

  Example:
  ```clojure
  (def pipeline-structure
    `(; ...
      (alias \"tests\"
        (run
          unit-test
          acceptance-test))
      (junction
        should-deploy?
        (run
          deploy-ci
          deploy-qa)
        do-nothing)))
  ```
  "
  [ & steps]
  (fn [args ctx]
    (run-steps-in-sequence args ctx steps)))

(defn- child-context [parent-ctx child-number]
  (let [parent-step-id (:step-id parent-ctx)
        child-step-id  (step-id/child-id parent-step-id child-number)]
    (assoc parent-ctx :step-id child-step-id)))

(defn ^{:display-type :parallel} junction
  "Build step that executes the first child and, depending on its success, the second or third child.
  Commonly used for conditional, if-then-else logic in pipelines.

  Example:
  ```clojure
  (def pipeline-structure
    `(; ...
      (junction
        should-do-something?
        step-to-run-if-success
        step-to-run-if-not-success)))
  ```"
  [condition-step success-step failure-step]
  (fn [args ctx]
    (post-process-container-results
      (let [condition-step-result (execution/execute-step args (child-context ctx 1) condition-step)
            new-args (serial-step-result-producer/args-for-subsequent-step args args condition-step-result)]
        (if (= :success (:status condition-step-result))
          (execution/execute-step new-args (child-context ctx 2) success-step)
          (execution/execute-step new-args (child-context ctx 3) failure-step))))))

(defn ^{:is-alias true} alias
  "Just runs child but child is displayed with the given alias in visualization.

  Example:
  ```clojure
  (def pipeline-structure
    `(; ...
      (alias \"tests\"
        (run
          unit-test
          acceptance-test)))
  ```"
  [alias child]
  (run child))

(defn with-workspace
  "Runs given steps with a clean workspace given to child step as :cwd argument.
  Commonly used if a build step needs some temporary directory to run, e.g. clone repositories and run build tasks.
  The given workspace is not persistent, it only exists for the runtime of the build step and is deleted afterwards.
  Long-living artifacts should be stored in an external artifact repository or using additional libraries like [`lambdacd-artifacts`](https://github.com/flosell/lambdacd-artifacts).

  Example:
  ```clojure
  (defn some-step [args ctx]
    (println \"The working directory:\" (:cwd args)))

  (def pipeline-structure
    `(; ...
      (with-workspace
        some-step
        some-other-step)))
  ```
  "
  [& steps]
  (fn [args ctx]
    (let [home-dir (:home-dir (:config ctx))
          temp-dir (temp-util/create-temp-dir home-dir)
          new-args  (assoc args :cwd temp-dir)]
      (temp-util/with-temp temp-dir
                       (run-steps-in-sequence new-args ctx steps)))))
