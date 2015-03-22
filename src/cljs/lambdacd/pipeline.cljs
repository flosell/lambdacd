(ns lambdacd.pipeline
  (:require-macros [cljs.core.async.macros :refer [go go-loop]])
  (:require [reagent.core :as reagent :refer [atom]]
            [secretary.core :as secretary :refer-macros [defroute]]
            [goog.events :as events]
            [goog.history.EventType :as EventType]
            [reagent.session :as session]
            [cljs.core.async :as async]
            [lambdacd.utils :as utils]
            [lambdacd.api :as api]))

(declare build-step-component)


(defn container-build-step-component [step-id status children name output-atom ul-or-ol on-click-fn retrigger-elem build-number]
  [:li {:key step-id :data-status status :on-click on-click-fn}
   [:span {:class "build-step"} name ]
   retrigger-elem
   [ul-or-ol (map #(build-step-component  % output-atom build-number) children)]])

(defn click-handler [handler]
  (fn [evt]
    (handler)
    (.stopPropagation evt)))

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

(defn can-be-retriggered? [step]
  (let [step-id (:step-id step)
        is-not-nested (= (count step-id) 1) ;; this is an implementation detail, retriggering of nested steps not properly implemented yet
        is-finished (is-finished step)]
    (and is-finished is-not-nested)))

(defn retrigger [build-number build-step]
  (api/retrigger build-number (first (:step-id build-step))))

(defn retrigger-component [build-number build-step]
  (if (can-be-retriggered? build-step)
    [:i {:class "fa fa-repeat retrigger" :on-click (click-handler #(retrigger build-number build-step))}]))

(defn manualtrigger-component [build-step]
  (let [result (:result build-step)
        trigger-id (:trigger-id result)]
    (if (and trigger-id (not (is-finished build-step)))
      [:i {:class "fa fa-play trigger" :on-click (click-handler #(manual-trigger result))}])))

(defn build-step-component [build-step output-atom build-number]
  (let [result (:result build-step)
        step-id (str (:step-id build-step))
        status (:status result)
        name (:name build-step)
        children (:children build-step)
        trigger-id (:trigger-id result)
        display-output (click-handler #(reset! output-atom (:out result)))
        retrigger-elem (retrigger-component build-number build-step)]
    (case (:type build-step)
      "parallel"  (container-build-step-component step-id status children name output-atom :ul display-output retrigger-elem build-number)
      "container" (container-build-step-component step-id status children name output-atom :ol display-output retrigger-elem build-number)
      [:li { :key step-id :data-status status :on-click display-output }
       [:span {:class "build-step"} name]
       (manualtrigger-component build-step)
       (retrigger-component build-number build-step)])))
