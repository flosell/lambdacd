(ns lambdacd.testcases
  (:require [reagent.core :as reagent :refer [atom]]
            [lambdacd.ui-core :as ui-core]
            [lambdacd.pipeline :as pipeline]))
(defn- background [color]
  {:style {:background-color color}})

(defn fake-history-component [& _]
  [:div (background "lightblue") "here is history"])

(defn fake-current-build-component [& _]
  [:div (background "lightgreen") "here is current build"])

(defn fake-header-component [& _]
  [:div (background "lightyellow") "some header"])


(defn render [component]
  (reagent/render-component component (.getElementById js/document "content")))

(defn main []
  (let [build-number           (atom 0)
        step-id                (atom [42])
        history                (atom [])
        state                  (atom {})
        output-details-visible (atom false)
        connection-lost        (atom false)]
    (render [#'ui-core/root build-number
                            step-id
                            history
                            state
                            output-details-visible
                            connection-lost

                            fake-history-component
                            fake-current-build-component
                            fake-header-component])))
(defn main-connection-lost []
  (let [build-number           (atom 0)
        step-id                (atom [42])
        history                (atom [])
        state                  (atom {})
        output-details-visible (atom false)
        connection-lost        (atom true)]
    (render [#'ui-core/root build-number
                            step-id
                            history
                            state
                            output-details-visible
                            connection-lost

                            fake-history-component
                            fake-current-build-component
                            fake-header-component])))

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
    (render
      [#'pipeline/pipeline-component build-number build-state-atom])))