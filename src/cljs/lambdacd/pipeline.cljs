(ns lambdacd.pipeline
  (:require-macros [cljs.core.async.macros :refer [go go-loop]])
  (:require [reagent.core :as reagent :refer [atom]]
            [lambdacd.utils :refer [click-handler classes append-components]]
            [lambdacd.api :as api]
            [lambdacd.route :as route]
            [clojure.string :as string]
            [lambdacd.db :as db]
            [lambdacd.time :as time]
            [re-frame.core :as re-frame]
            [clojure.string :as s]))

(declare build-step-component) ;; mutual recursion

(defn format-build-step-duration [{status :status
                                   has-been-waiting   :has-been-waiting
                                   most-recent-update :most-recent-update-at
                                   first-update :first-updated-at}]
  (if (or has-been-waiting
          (not status))
    ""
    (let [duration-in-sec (time/seconds-between-two-timestamps first-update most-recent-update)
          duration (time/format-duration-short duration-in-sec)]
      duration)))

(defn step-component-with []
  (let [step-id-to-display-atom (re-frame/subscribe [::db/step-id])]
    (fn [{step-id :step-id {status :status :as step-result} :result name :name } build-number children]
      (let [step-id-to-display @step-id-to-display-atom
            status-class (str  "pipeline__step--" (or status "no-status") (if (= step-id step-id-to-display) " pipeline__step--active"))
            pipeline-step-class "pipeline__step"
            formatted-duration (format-build-step-duration step-result)
            name-and-duration (if (s/blank? formatted-duration) name (str name " (" formatted-duration ")"))]
        (append-components [:li { :key (str step-id) :data-status status :class (classes status-class pipeline-step-class)}
                            [:a {:class "step-link" :href (route/for-build-and-step-id build-number step-id)}
                              [:span {:class "build-step"} name-and-duration]]] children)))))

(defn container-build-step-component [{children :children step-id :step-id :as build-step } type retrigger-elem kill-elem build-number]
  (let [is-expanded (re-frame/subscribe [::db/step-expanded? step-id])]
    (fn [{children :children step-id :step-id :as build-step } type retrigger-elem kill-elem build-number]
      (let [ul-or-ol (if (= type :sequential) :ol :ul)
            modifier-class (if (= type :sequential) "pipeline__step-container--sequential" "pipeline__step-container--parallel")
            container-class "pipeline__step-container"
            expansion-icon-class (if @is-expanded "fa-minus" "fa-plus")
            expander [:i {:class (str "fa " expansion-icon-class " pipeline__step__action-button") :on-click (fn [event]
                                                                                                               (re-frame/dispatch [::db/toggle-step-expanded step-id])
                                                                                                               nil)}]]
        [step-component-with build-step
                             build-number
                             [retrigger-elem kill-elem
                              expander
                              (if @is-expanded
                                [ul-or-ol {:class (classes container-class modifier-class)}
                                 (for [child children]
                                   ^{:key (:step-id child)} [build-step-component child])])]]))))

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

(defn is-already-killed [{result :result}]
  (or
    (:received-kill result)
    (:processed-kill result)))

(defn has-dependencies [step]
  (:has-dependencies step))

(defn- is-waiting [step]
  (let [status (:status (:result step))]
    (= "waiting" status)))

(defn- has-status [step]
  (let [status (:status (:result step))]
    (not (nil? status))))

(defn- step-id-for [build-step]
  (string/join "-" (:step-id build-step)))

(defn retrigger-component [build-number build-step]
  (if (is-finished build-step)
    (if (has-dependencies build-step)
      [:i {:class "fa fa-repeat pipeline__step__action-button pipeline__step__action-button--disabled" :title "this step can not be safely retriggered as it depends on previous build steps"}]
      [:i {:class "fa fa-repeat pipeline__step__action-button" :on-click (click-handler #(api/retrigger build-number (step-id-for build-step)))}])))

(defn- can-be-killed? [step]
  (and
    (has-status step)
    (not (is-finished step))
    (not (is-waiting step))
    (not (is-already-killed step))))

(defn kill-component [build-number build-step]
  (if (can-be-killed? build-step)
    [:i {:class "fa fa-times pipeline__step__action-button" :on-click (click-handler #(api/kill build-number (step-id-for build-step)))}]))

(defn manualtrigger-component [build-step]
  (let [result (:result build-step)
        trigger-id (:trigger-id result)]
    (if (and trigger-id (not (is-finished build-step)))
      [:i {:class "fa fa-play pipeline__step__action-button" :on-click (click-handler #(manual-trigger result))}])))

(defn build-step-component []
  (let [build-number-subscription (re-frame/subscribe [::db/build-number])]
    (fn [build-step _]
      (let [build-number   @build-number-subscription
            retrigger-elem (retrigger-component build-number build-step)
            kill-elem      (kill-component build-number build-step)]
        (case (:type build-step)
          "parallel"  [container-build-step-component build-step :parallel retrigger-elem kill-elem build-number]
          "container" [container-build-step-component build-step :sequential retrigger-elem kill-elem build-number]
          [step-component-with build-step build-number
                               [(manualtrigger-component build-step)
                                retrigger-elem
                                (kill-component build-number build-step)]])))))

(defn- control-disabled-if [b]
  (if b
    "pipeline__controls__control--disabled"
    ""))

(defn- control-active-if [b]
  (if b
    "pipeline__controls__control--active"
    ""))

(defn pipeline-controls []
  (let [all-expanded?    (re-frame/subscribe [::db/all-expanded?])
        all-collapsed?   (re-frame/subscribe [::db/all-collapsed?])
        expand-active?   (re-frame/subscribe [::db/expand-active-active?])
        expand-failures? (re-frame/subscribe [::db/expand-failures-active?])]
    (fn []
      [:ul {:class "pipeline__controls"}
       [:li {:class (str "pipeline__controls__control " (control-disabled-if @all-expanded?)) :on-click #(re-frame/dispatch [::db/set-all-expanded])} "Expand all"]
       [:li {:class (str "pipeline__controls__control " (control-disabled-if @all-collapsed?)) :on-click #(re-frame/dispatch [::db/set-all-collapsed]) } "Collapse all"]
       [:li {:class (str "pipeline__controls__control " (control-active-if @expand-active?)) :on-click #(re-frame/dispatch [::db/toggle-expand-active]) } "Expand active"]
       [:li {:class (str "pipeline__controls__control " (control-active-if @expand-failures?)) :on-click #(re-frame/dispatch [::db/toggle-expand-failures]) } "Expand failures"]])))

(defn pipeline-component []
  (let [build-state-atom (re-frame/subscribe [::db/pipeline-state])]
    (fn []
      [:div {:class "pipeline" :key "build-pipeline"}
       [pipeline-controls]
       [:ol {:class "pipeline__step-container pipeline__step-container--sequential"}
        (doall
          (for [step @build-state-atom]
            ^{:key (:step-id step)} [build-step-component step]))]])))