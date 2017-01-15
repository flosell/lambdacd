(ns lambdacd.output
  (:require [reagent.core :refer [atom]]
            [re-frame.core :as re-frame]
            [lambdacd.db :as db]
            [clojure.string :as s]
            [lambdacd.console-output-processor :as console-output-processor]
            [lambdacd.utils :as utils]))

(defn raw-step-results-component []
  (let [current-step-result      (re-frame/subscribe [::db/current-step-result])
        raw-step-results-visible (re-frame/subscribe [::db/raw-step-results-visible])]
    (fn []
      [:div
       [:h3 "Complete Step Result"]
       [:button {:on-click #(re-frame/dispatch [::db/toggle-raw-step-results-visible])}
        (if @raw-step-results-visible "hide" "show")]
       (if @raw-step-results-visible
         [:pre {:class "step-results__raw-step-results"}
          (utils/pretty-print-map @current-step-result)])])))

(declare details-section)

(defn- detail-component [detail]
  [:li {:key (str (:label detail) (hash detail))}
   (cond
     (:href detail) [:a {:href (:href detail)
                         :target "_blank"} (:label detail)]
     (:raw detail) [:div
                     [:span (:label detail)]
                     [:pre (:raw detail)]]
     :else [:span (:label detail)])
   (if (:details detail)
     (details-section (:details detail)))])

(defn- details-section [details]
  [:ul (map detail-component details)])

(defn details-component []
  (let [result (re-frame/subscribe [::db/current-step-result])]
    (fn []
      (if (:details (:result @result))
        [:div {:class "details-container"}
         [:h3 "Details"]
         (details-section (:details (:result @result)))]))))

(defn- scroll-to-bottom []
  (js/window.scrollTo 0 js/document.body.scrollHeight ))

(defn- is-scrolled-to-bottom []
  (let [delta (- (- js/document.body.scrollHeight js/document.body.scrollTop) js/document.body.clientHeight)
        is-bottom (< delta 20)]
    is-bottom))

(defn scroll-wrapper [component]
  (let [scrolled-to-bottom-before-update (atom false)
        before-update #(reset! scrolled-to-bottom-before-update (is-scrolled-to-bottom))
        after-update #(if @scrolled-to-bottom-before-update (scroll-to-bottom))
        wrapped-component (with-meta component
                             {:component-will-update before-update
                              :component-did-update after-update})]
    [wrapped-component]))

(defn status-to-string [status]
  (s/upper-case status))

(defn- still-active? [status]
  (or (nil? status) (= status "running") (= status "waiting")))

(defn- received-kill? [result]
  (and
    (:received-kill result)
    (not= "killed" (:status result))))

(defn- processed-kill? [result]
  (and
    (:processed-kill result)
    (not= "killed" (:status result))))

(defn- enhanced-output [{status :status output :out :as result}]
  (cond
    (processed-kill? result) (str output "\n\n" "Step received kill and is shutting down...")
    (received-kill? result) (str output "\n\n" "LambdaCD received kill and waiting for build step to react to it...")
    (still-active? status) output
    :else (str output "\n\n" "Step is finished: " (status-to-string status))))


(defn- fragment-style->classes [fragment]
  (->> fragment
       (filter #(true? (second %)))
       (map first)
       (map {:bold      "console-output__line--bold"
             :italic    "console-output__line--italic"
             :underline "console-output__line--underline"})
       (filter #(not (nil? %)))))

(defn- background-color->classes [fragment]
  (if (:background fragment)
    [(str "console-output__line--bg-" (:background fragment))]
    []))

(defn- foreground-color->classes [fragment]
  (if (:foreground fragment)
    [(str "console-output__line--fg-" (:foreground fragment))]
    []))

(defn ansi-fragment->classes [fragment]
  (->> (concat
         (fragment-style->classes fragment)
         (background-color->classes fragment)
         (foreground-color->classes fragment))
       (s/join " ")))

(defn- console-output-line-fragment [idx fragment]
  [:span {:key (str idx "-" (hash fragment)) :class (ansi-fragment->classes fragment)} (:text fragment)])

(defn- console-output-line [idx fragments]
  [:pre {:class "console-output__line" :key (str idx "-" (hash fragments))}
   (map-indexed console-output-line-fragment fragments)])

(defn console-component []
  (let [current-step-result (re-frame/subscribe [::db/current-step-result])]
    (fn []
      (if (not (nil? (:out (:result @current-step-result))))
        [:div
         [:h3 "Console Output"]
         (let [lines (console-output-processor/process-ascii-escape-characters (enhanced-output (:result @current-step-result)))]
           [:div {:class "console-output"}
            (map-indexed console-output-line lines)])]
        [:div]))))

(defn output-renderer [current-step-id]
  (if current-step-id
    [:div {:class "results"}
     [details-component]
     [raw-step-results-component]
     (scroll-wrapper console-component)]
    [:pre "Click on a build step to display details."]))

(defn output-component []
  (let [current-step-id (re-frame/subscribe [::db/step-id])]
    (fn []
      [output-renderer @current-step-id])))
