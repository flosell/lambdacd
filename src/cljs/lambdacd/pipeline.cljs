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

(defn step-component-with [{step-id :step-id {status :status :as step-result} :result name :name } build-number children step-id-to-display]
  (let [status-class (str  "pipeline__step--" (or status "no-status") (if (= step-id step-id-to-display) " pipeline__step--active"))
        pipeline-step-class "pipeline__step"
        formatted-duration (format-build-step-duration step-result)
        name-and-duration (if (s/blank? formatted-duration) name (str name " (" formatted-duration ")"))]
    (append-components [:li { :key (str step-id) :data-status status :class (classes status-class pipeline-step-class)}
                        [:a {:class "step-link" :href (route/for-build-and-step-id build-number step-id)}
                          [:span {:class "build-step"} name-and-duration]]] children)))

(defn container-build-step-component [{children :children step-id :step-id :as build-step } type retrigger-elem kill-elem build-number step-id-to-display expanded-step-ids]
  (let [ul-or-ol (if (= type :sequential) :ol :ul)
        modifier-class (if (= type :sequential) "pipeline__step-container--sequential" "pipeline__step-container--parallel")
        container-class "pipeline__step-container"
        is-expanded (contains? expanded-step-ids step-id)
        expansion-icon-class (if is-expanded "fa-minus" "fa-plus")
        expander [:i {:class (str "fa " expansion-icon-class " pipeline__step__action-button") :on-click (fn [event]
                                                                                                           (re-frame/dispatch [::db/toggle-step-expanded step-id])
                                                                                                           nil)}]]
    (step-component-with build-step
                         build-number
                         [retrigger-elem kill-elem
                          expander
                          (if is-expanded
                            [ul-or-ol {:class (classes container-class modifier-class)} (map #(build-step-component % build-number step-id-to-display expanded-step-ids) children)])]
                         step-id-to-display)))

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
    (not (is-waiting step))))

(defn kill-component [build-number build-step]
  (if (can-be-killed? build-step)
    [:i {:class "fa fa-times pipeline__step__action-button" :on-click (click-handler #(api/kill build-number (step-id-for build-step)))}]))

(defn manualtrigger-component [build-step]
  (let [result (:result build-step)
        trigger-id (:trigger-id result)]
    (if (and trigger-id (not (is-finished build-step)))
      [:i {:class "fa fa-play pipeline__step__action-button" :on-click (click-handler #(manual-trigger result))}])))

(defn build-step-component [build-step build-number step-id-to-display expanded-step-ids]
  (let [retrigger-elem (retrigger-component build-number build-step)
        kill-elem      (kill-component build-number build-step)]
    (case (:type build-step)
      "parallel"  (container-build-step-component build-step :parallel retrigger-elem kill-elem build-number step-id-to-display expanded-step-ids)
      "container" (container-build-step-component build-step :sequential retrigger-elem kill-elem build-number step-id-to-display expanded-step-ids)
      (step-component-with build-step build-number
                           [(manualtrigger-component build-step)
                            retrigger-elem
                            (kill-component build-number build-step)]
                           step-id-to-display))))

(defn pipeline-renderer [build-number build-state-atom step-id-to-display expanded-step-ids]
  [:div {:class "pipeline" :key "build-pipeline"}
   [:ol {:class "pipeline__step-container pipeline__step-container--sequential"}
    (map #(build-step-component % build-number step-id-to-display expanded-step-ids) @build-state-atom)]])

(defn pipeline-component []
  (let [current-build-number (re-frame/subscribe [::db/build-number])
        step-id-to-display-atom (re-frame/subscribe [::db/step-id])
        state-atom (re-frame/subscribe [::db/pipeline-state])
        expanded-step-ids (re-frame/subscribe [::db/expanded-step-ids])]
    (fn []
      [pipeline-renderer @current-build-number state-atom @step-id-to-display-atom @expanded-step-ids])))