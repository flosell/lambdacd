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
            [clojure.string :as s]
            [lambdacd.utils :as utils]))

(defn is-finished [step]
  (let [status (:status (:result step))
        is-finished (or (= "success" status) (= "failure" status) (= "killed" status))]
    is-finished))

(defn is-already-killed [{result :result}]
  (or
    (:received-kill result)
    (:processed-kill result)))

(defn is-being-killed? [{result :result}]
  (and
    (:received-kill result)
    (not= "killed" (:status result))))

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

(defn- can-be-killed? [step]
  (and
    (has-status step)
    (not (is-finished step))
    (not (is-already-killed step))))

(defn- type->ul-or-ol [type]
  (case type
    "parallel" :ul
    "container" :ol
    nil))

(defn- step-icon-classes [status]
  (case status
    "kill" "fa fa-times"
    "trigger" "fa fa-play"
    "retrigger" "fa fa-repeat"
    "expand" "fa fa-plus"
    "killed"  "fa fa-bug"
    "being-killed" "fa fa-times fa-spin"
    "collapse" "fa fa-minus"))

(defn step-icon [status build-step]
  [:i {:class (step-icon-classes status) :alt (str status " " (:name build-step))}])

(defn retrigger-component [build-number build-step]
  (if (is-finished build-step)
    (if (has-dependencies build-step)
      [:i {:class "fa fa-repeat pipeline__step__action-button pipeline__step__action-button--disabled" :title "this step can not be safely retriggered as it depends on previous build steps"}]
      [:a {:class    "pipeline__step__action-button"
           :on-click (click-handler #(api/retrigger build-number (step-id-for build-step)))}
       (step-icon "retrigger" build-step)])))

(defn ask-for [parameters]
  (into {} (doall (map (fn [[param-name param-config]]
                         [param-name (js/prompt (str "Please enter a value for " (name param-name) ": " (:desc param-config)))]) parameters))))

(defn manual-trigger [{trigger-id :trigger-id parameters :parameters}]
  (if parameters
    (api/trigger trigger-id (ask-for parameters))
    (api/trigger trigger-id {})))

(defn kill-component [build-number build-step]
  (cond
    (can-be-killed? build-step) [:a {:class    "pipeline__step__action-button"
                                     :on-click (click-handler #(api/kill build-number (step-id-for build-step)))}
                                 (step-icon "kill" build-step)]
    (is-being-killed? build-step) [:span {:class "pipeline__step__action-button pipeline__step__action-button--disabled"}
                                   (step-icon "being-killed" build-step)]))

(defn active-manual-trigger-component [build-step]
  [:a {:class    "pipeline__step__action-button"
       :on-click (click-handler #(manual-trigger (:result build-step)))}
   (step-icon "trigger" build-step)])

(defn inactive-manual-trigger-component []
  [:i {:class "fa fa-play pipeline__step__action-button pipeline__step__action-button--disabled"}])

(defn manualtrigger-component [build-step]
  (let [trigger-id        (:trigger-id (:result build-step))
        not-finished      (not (is-finished build-step))
        is-manual-trigger (= "manual-trigger" (:type build-step))]
    (cond
      (and not-finished trigger-id)        [active-manual-trigger-component build-step]
      (and not-finished is-manual-trigger) [inactive-manual-trigger-component])))

(defn- expandable? [{type :type}]
  (or (= "parallel" type) (= "container" type)))

(defn- expander-button [{step-id :step-id}]
(let [is-expanded (re-frame/subscribe [::db/step-expanded? step-id])]
  (fn [{step-id :step-id :as build-step}]
    (if (expandable? build-step)
      [:a {:class    "pipeline__step__action-button"
           :on-click (fn [event]
                       (re-frame/dispatch [::db/toggle-step-expanded step-id])
                       nil)}
       (if @is-expanded
         (step-icon "collapse" build-step)
         (step-icon "expand" build-step))]))))

(declare build-step) ;; mutual recursion

(defn format-build-step-duration [{status             :status
                                   has-been-waiting   :has-been-waiting
                                   most-recent-update :most-recent-update-at
                                   first-update       :first-updated-at}]
  (if (or has-been-waiting
          (not status))
    ""
    (let [duration-in-sec (time/seconds-between-two-timestamps first-update most-recent-update)
          duration (time/format-duration-short duration-in-sec)]
      duration)))

(defn- step-label [{step-id :step-id {status :status :as step-result} :result name :name} build-number]
  (let [step-id-to-display-atom (re-frame/subscribe [::db/step-id])]
    (let [formatted-duration (format-build-step-duration step-result)
          name-and-duration (if (s/blank? formatted-duration) name (str name " (" formatted-duration ")"))]
      [:a {:class (classes "step-link" (if (= step-id @step-id-to-display-atom) "step-link--active"))
           :href (route/for-build-and-step-id build-number step-id)}
       [:span {:class "build-step"} name-and-duration]])))

(defn- status-class [status]
  (str "pipeline__step--" (or status "no-status")))

(defn- step-children [step-id type children build-number]
  (let [is-expanded (re-frame/subscribe [::db/step-expanded? step-id])]
    (fn [step-id ul-or-ol children build-number]
      (let [sequential-or-parallel (if (= ul-or-ol :ol)
                                     "pipeline__step-container--sequential"
                                     "pipeline__step-container--parallel")]
        (if (and @is-expanded ul-or-ol)
          [ul-or-ol {:class (classes "pipeline__step-container" sequential-or-parallel)}
           (for [child children]
             ^{:key (:step-id child)} [build-step child build-number])])))))

(defn- build-step [build-step build-number]
  (let []
    (fn [{step-id :step-id {status :status} :result type :type children :children :as build-step} build-number]
      [:li {:key         (str step-id)
            :data-status status
            :class       (classes "pipeline__step" (status-class status))}
       [:div {:class "pipeline__step-desc-container"}
        [retrigger-component build-number build-step]
        [kill-component build-number build-step]
        [step-label build-step build-number]
        [manualtrigger-component build-step]
        [expander-button build-step]]

       [step-children step-id (type->ul-or-ol type) children build-number]])))

(defn- control-disabled-if [b]
  (if b "pipeline__controls__control--disabled"))

(defn- control-active-if [b]
  (if b "pipeline__controls__control--active"))

(defn pipeline-controls []
  (let [all-expanded? (re-frame/subscribe [::db/all-expanded?])
        all-collapsed? (re-frame/subscribe [::db/all-collapsed?])
        expand-active? (re-frame/subscribe [::db/expand-active-active?])
        expand-failures? (re-frame/subscribe [::db/expand-failures-active?])]
    (fn []
      [:ul {:class "pipeline__controls"}
       [:li [:a {:class (classes "pipeline__controls__control" (control-disabled-if @all-expanded?)) :on-click #(re-frame/dispatch [::db/set-all-expanded])} "Expand all"]]
       [:li [:a {:class (classes "pipeline__controls__control" (control-disabled-if @all-collapsed?)) :on-click #(re-frame/dispatch [::db/set-all-collapsed])} "Collapse all"]]
       [:li [:a {:class (classes "pipeline__controls__control" (control-active-if @expand-active?)) :on-click #(re-frame/dispatch [::db/toggle-expand-active])} "Expand active"]]
       [:li [:a {:class (classes "pipeline__controls__control" (control-active-if @expand-failures?)) :on-click #(re-frame/dispatch [::db/toggle-expand-failures])} "Expand failures"]]])))

(defn old-pipeline-structure-warning [build-steps]
  (if (:pipeline-structure-fallback (first build-steps))
    [:ul {:class "pipeline__controls"}
     [:li {:class "pipeline__controls__control warning"} [:i {:class "fa fa-exclamation warning-icon"}] "This is an old build with no stored pipeline structure. Using current structure as a best guess. Display and retriggering might not work as expected."]]))

(defn pipeline-component []
  (let [build-state-atom (re-frame/subscribe [::db/pipeline-state])
        build-number-subscription (re-frame/subscribe [::db/build-number])]
    (fn []
      [:div {:class "pipeline" :key "build-pipeline"}
       [pipeline-controls]
       [old-pipeline-structure-warning @build-state-atom]
       [:ol {:class "pipeline__step-container pipeline__step-container--sequential"}
        (doall
          (for [step @build-state-atom]
            ^{:key (:step-id step)} [build-step step @build-number-subscription]))]])))
