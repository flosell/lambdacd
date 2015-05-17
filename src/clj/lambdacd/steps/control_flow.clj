(ns lambdacd.steps.control-flow
  "control flow elements for a pipeline: steps that control the way their child-steps are being run"
  (:require [lambdacd.core :as core]
            [clojure.core.async :as async]
            [lambdacd.util :as util]
            [lambdacd.steps.support :as support]))



(defn- post-process-container-results [result]
  (let [outputs (vals (:outputs result))
        globals (support/merge-globals outputs)
        merged-step-results (support/merge-step-results outputs)]
    (merge merged-step-results result {:global globals})))

(defn- wait-for-success-on [channels]
  (let [merged (async/merge channels)
        filtered-by-success (async/filter< #(= :success (:status %)) merged)]
    (async/<!! filtered-by-success)))


(defn- execute-step-with-channel [args [step-with-id ch]]
 (core/execute-step args step-with-id :result-channel ch))

(defn- chan-with-idx [idx c]
  (let [out-ch (async/chan 10)]
    (async/go
      (loop []
        (if-let [v (async/<! c)]
          (do
            (async/>! out-ch [idx v])
            (recur))
          (async/close! out-ch))))
    out-ch))


(defn unify-statuses [statuses]
  (let [has-failed (util/contains-value? :failure statuses)
        has-running (util/contains-value? :running statuses)
        all-waiting (every? #(= % :waiting) statuses)
        one-ok (util/contains-value? :success statuses)]
    (cond
      has-failed :failure
      one-ok :success
      has-running :running
      all-waiting :waiting
      :else :unknown)))

(defn- send-new-status [statuses out-ch]
  (let [statuses (vals statuses)
        unified (unify-statuses statuses)]
    (async/>!! out-ch [:status unified])))

(defn- process-inheritance [status-with-indexes]
  (let [out-ch (async/chan 100)]
    (async/go
      (loop [statuses {}]
        (if-let [[idx [k v]] (async/<! status-with-indexes)]
          (if (= :status k)
            (let [new-statuses (assoc statuses idx v)]
              (send-new-status new-statuses out-ch)
              (recur new-statuses))
            (recur statuses))
          (async/close! out-ch))))
    out-ch))

(defn- inherit-from [result-channels own-result-channel]
  (let [channels-with-idx (map-indexed #(chan-with-idx %1 %2) result-channels)
        all-channels (async/merge channels-with-idx)
        status-channel (process-inheritance all-channels)]
    (async/pipe status-channel own-result-channel)))

(defn- inheriting-result-channels-for-steps [ctx steps-and-ids]
  (let [result-channel (:result-channel ctx)
        result-channels (repeatedly (count steps-and-ids) #(async/chan 10))
        steps-ids-and-channels (map vector steps-and-ids result-channels)
        _ (inherit-from result-channels result-channel)]
    steps-ids-and-channels))

(defn- step-producer-returning-with-first-successful [ctx args steps-and-ids]
  (let [steps-ids-and-channels (inheriting-result-channels-for-steps ctx steps-and-ids)
        step-result-channels (map #(async/go (execute-step-with-channel args %)) steps-ids-and-channels)
        result (wait-for-success-on step-result-channels)]
    (if (nil? result)
      [{:status :failure}]
      [result])))

(defn ^{:display-type :parallel} either [& steps]
  (fn [args ctx]
    (let [kill-switch (atom false)
          execute-output (core/execute-steps steps args (core/new-base-context-for ctx)
                                                     :is-killed kill-switch
                                                     :step-result-producer (partial step-producer-returning-with-first-successful ctx))]
      (reset! kill-switch true)
      (if (= :success (:status execute-output))
        (first (vals (:outputs execute-output)))
        execute-output))))

(defn- parallel-step-result-producer [ctx args steps-and-ids]
  (let [steps-ids-and-channels (inheriting-result-channels-for-steps ctx steps-and-ids)]
    (pmap #(execute-step-with-channel args %) steps-ids-and-channels)))

(defn- execute-steps-in-parallel [ctx steps args step-id]
  (core/execute-steps steps args step-id
                      :step-result-producer (partial parallel-step-result-producer ctx)))

(defn ^{:display-type :parallel} in-parallel [& steps]
  (fn [args ctx]
    (post-process-container-results
      (execute-steps-in-parallel ctx steps args (core/new-base-context-for ctx)))))


(defn ^{:display-type :container} in-cwd [cwd & steps]
  (fn [args ctx]
    (post-process-container-results
      (core/execute-steps steps (assoc args :cwd cwd) (core/new-base-context-for ctx)))))
