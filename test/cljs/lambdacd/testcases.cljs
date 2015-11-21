(ns lambdacd.testcases
  (:require [reagent.core :as reagent :refer [atom]]
            [lambdacd.ui-core :as ui-core]
            [lambdacd.history :as history]
            [cljs-time.format :as format]
            [lambdacd.pipeline :as pipeline]
            [cljs-time.core :as t]
            [lambdacd.time :as time]))
(defn- background [color]
  {:style {:background-color color}})

(defn fake-history-component [& _]
  [:div (background "lightblue") "here is history"])

(defn fake-current-build-component [& _]
  [:div (background "lightgreen") "here is current build"])

(defn fake-header-component [& _]
  [:div (background "lightyellow") "some header"])

(defn main []
  (let [build-number           (atom 0)
        state                  (atom {})
        connection-not-lost    (atom false)]
    [#'ui-core/root build-number
                            state
                            connection-not-lost

                            fake-history-component
                            fake-current-build-component]))
(defn main-connection-lost []
  (let [build-number           (atom 0)
        step-id                (atom [42])
        history                (atom [])
        state                  (atom {})
        output-details-visible (atom false)
        connection-lost        (atom true)]
    [#'ui-core/root build-number
                            state
                            connection-lost

                            fake-history-component
                            fake-current-build-component]))

(defn normal-pipeline []
  (let [build-state-atom (atom [{:type "parallel"
                                 :name "either"
                                 :step-id [1]
                                 :result {:status "success"}
                                 :children [{:name "wait-for-git"
                                             :step-id [1 1]
                                             :result {:status "success"}}
                                            {:name "wait-for-manual-trigger"
                                             :step-id [2 1]
                                             :result {:status "killed"}}
                                            {:name "always-waiting"
                                             :step-id [3 1]
                                             :result {:status "waiting"}}]}
                                {:name "build"
                                 :step-id [2]
                                 :result {:status "success"}}
                                {:name "deploy"
                                 :type "parallel"
                                 :step-id [3]
                                 :result {:status "running"}
                                 :children [{:name "deploy-ci"
                                             :type "container"
                                             :step-id [1 3]
                                             :result {:status "running"}
                                             :children [{:name "deploy"
                                                         :step-id [1 1 3]
                                                         :result {:status "running"}}
                                                        {:name "smoke-test"
                                                         :step-id [2 1 3]
                                                         :result {}}]}
                                            {:name "deploy-qa"
                                             :type "container"
                                             :step-id [2 3]
                                             :result {:status "failure"}
                                             :children [{:name "deploy"
                                                         :step-id [1 2 3]
                                                         :result {:status "success"}}
                                                        {:name "smoke-test"
                                                         :step-id [2 2 3]
                                                         :result {:status "failure"}}]}]}])
        build-number     1]
      [#'pipeline/pipeline-renderer build-number build-state-atom 42 []]))

(defn formatted-time [t]
  (format/unparse time/formatter t))

(def a-few-seconds-ago (-> (t/now)
                           (t/minus (t/seconds 3))))



(defn normal-history []
  [:div {:style {:display "flex"}}
    [#'history/build-history-renderer [{:build-number 1
                                         :status "killed"
                                         :most-recent-update-at (formatted-time (t/now))
                                         :first-updated-at (formatted-time a-few-seconds-ago)}
                                        {:build-number 2
                                         :status "failure"
                                         :most-recent-update-at "2015-08-02T11:38:40.671Z"
                                         :first-updated-at "2015-08-02T11:37:31.272Z"}
                                        {:build-number 3
                                         :status "running"
                                         :most-recent-update-at "2015-08-02T11:37:40.671Z"
                                         :first-updated-at "2015-08-02T11:37:31.272Z"}
                                        {:build-number 4
                                         :status "waiting"
                                         :most-recent-update-at nil
                                         :first-updated-at nil}
                                        {:build-number 5
                                         :status "success"
                                         :most-recent-update-at "2015-08-02T22:50:55.671Z"
                                         :first-updated-at "2015-08-02T11:37:31.272Z"
                                         :retriggered 2}] 5]
   [:div {:style {:border  "solid yellow"
                  :display "flex"
                  :width "100000px"}} "neighboring content eating up all space"]])


(defn fake-pipeline [& _]
  [:div (background "lightsteelblue") "here is pipeline"])

(defn fake-output [& _]
  [:div (background "lightcyan") "here is output"])


(defn current-build-wrapper []
  (let [build-number           (atom 0)
        state                  (atom {})]
    [#'ui-core/current-build-component state build-number fake-pipeline fake-output fake-header-component]))

(def tc
  [{:id        "main-connection-lost"
    :component [#'main-connection-lost]}
   {:id        "normal-pipeline"
    :component [#'normal-pipeline]}
   {:id        "normal-history"
    :component [#'normal-history]}
   {:id        "current-build-wrapper"
    :component [#'current-build-wrapper]}])