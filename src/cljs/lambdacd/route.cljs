(ns lambdacd.route
  (:require [bidi.bidi :as bidi]
            [goog.history.EventType :as EventType]
            [goog.events :as events]
            [re-frame.core :as re-frame]
            [lambdacd.db :as db]
            [clojure.string :as string])
  (:import goog.History))

(def route
  ["/builds/"
    {[:buildnumber ] :build
     [:buildnumber "/" :step-id] :build-and-step-id}])

(defn- set-state [step-id-atom build-number step-id]
  (re-frame/dispatch [::db/build-number-updated build-number])
  (reset! step-id-atom step-id))

(defn- parse-step-id [step-id-string]
  (vec (map js/parseInt (string/split step-id-string #"-"))))

(defn dispatch-route [step-id-to-display-atom state-atom path]
  (let [{handler :handler params :route-params } (bidi/match-route route path)]
    (case handler
          :build             (do
                               (reset! state-atom nil)
                               (set-state step-id-to-display-atom (js/parseInt (:buildnumber params)) nil)
                               {:routing :ok})
          :build-and-step-id (do
                               (set-state step-id-to-display-atom (js/parseInt (:buildnumber params)) (parse-step-id (:step-id params)))
                               {:routing :ok})
          {:routing :failed })))

(defn for-build-number [build-number]
  (str "#" (bidi/path-for route :build :buildnumber build-number)))

(defn for-build-and-step-id [build-number step-id]
  (str "#" (bidi/path-for route :build-and-step-id :buildnumber build-number :step-id (string/join "-" step-id))))

; TODO: hacky global variable...
(def history (History.))

(defn- navigate [step-id-to-display-atom state-atom token]
  (let [nav-result (dispatch-route step-id-to-display-atom state-atom token)]
    (if (not= :ok (:routing nav-result))
      (.setToken history (:redirect-to nav-result)))))


(defn hook-browser-navigation! [step-id-to-display-atom state-atom]
  (doto history
    (events/listen
      EventType/NAVIGATE
      (fn [event]
        (navigate step-id-to-display-atom state-atom (.-token event))))
    (.setEnabled true)))

(defn set-build-number [build-number]
  (.setToken history (str "/builds/" build-number)))