(ns lambdacd.pipeline
  (:require-macros [cljs.core.async.macros :refer [go go-loop]])
  (:require [reagent.core :as reagent :refer [atom]]
            [lambdacd.utils :refer [click-handler]]
            [lambdacd.utils :refer [append-components]]
            [lambdacd.api :as api]
            [lambdacd.route :as route]
            [clojure.string :as string]))

(declare build-step-component) ;; mutual recursion

(defn step-component-with [{step-id :step-id {status :status} :result name :name } build-number children]
  (append-components [:li { :key (str step-id) :data-status status }
                      [:a {:class "step-link" :href (route/for-build-and-step-id build-number step-id)}
                        [:span {:class "build-step"} name]]] children))

(defn container-build-step-component [{children :children :as build-step } ul-or-ol retrigger-elem build-number]
  (step-component-with build-step build-number [retrigger-elem
                                               [ul-or-ol (map #(build-step-component % build-number) children)]]))

(defn ask-for [parameters]
  (into {} (doall (map (fn [[param-name param-config]]
                         [param-name (js/prompt (str "Please enter a value for " (name param-name) ": " (:desc param-config)))]) parameters))))

(defn manual-trigger [{ trigger-id :trigger-id parameters :parameters}]
  (if parameters
    (api/trigger trigger-id (ask-for parameters))
    (api/trigger trigger-id {})))

(defn is-finished [step]
  (let [status (:status (:result step))
        is-finished (or (= "success" status) (= "failure" status) (= "killed" status))]
    is-finished))

(defn- is-waiting [step]
  (let [status (:status (:result step))]
    (= "waiting" status)))

(defn- has-status [step]
  (let [status (:status (:result step))]
    (not (nil? status))))

(defn can-be-retriggered? [step]
  (is-finished step))

(defn- step-id-for [build-step]
  (string/join "-" (:step-id build-step)))

(defn retrigger-component [build-number build-step]
  (if (can-be-retriggered? build-step)
    [:i {:class "fa fa-repeat retrigger" :on-click (click-handler #(api/retrigger build-number (step-id-for build-step)))}]))

(defn- can-be-killed? [step]
  (and
    (has-status step)
    (not (is-finished step))
    (not (is-waiting step))))

(defn kill-component [build-number build-step]
  (if (can-be-killed? build-step)
    [:i {:class "fa fa-times kill" :on-click (click-handler #(api/kill build-number (step-id-for build-step)))}]))

(defn manualtrigger-component [build-step]
  (let [result (:result build-step)
        trigger-id (:trigger-id result)]
    (if (and trigger-id (not (is-finished build-step)))
      [:i {:class "fa fa-play trigger" :on-click (click-handler #(manual-trigger result))}])))

(defn build-step-component [build-step build-number]
  (let [retrigger-elem (retrigger-component build-number build-step)]
    (case (:type build-step)
      "parallel"  (container-build-step-component build-step :ul retrigger-elem build-number)
      "container" (container-build-step-component build-step :ol retrigger-elem build-number)
      (step-component-with build-step build-number
                           [(manualtrigger-component build-step)
                            retrigger-elem
                            (kill-component build-number build-step)] ))))


(defn pipeline-component [build-number build-state-atom]
  [:div {:id "pipeline" }
   [:ol
    (map #(build-step-component % build-number) @build-state-atom)]])