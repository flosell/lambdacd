(ns lambdacd.route
  (:require [bidi.bidi :as bidi]))

(def route
  [["/builds/" :buildnumber] :build])

(defn dispatch-route [build-number-atom path]
  (let [{handler :handler params :route-params } (bidi/match-route route path)]
    (case handler
          :build (do
                   (reset! build-number-atom (:buildnumber params))
                   {:routing :ok})
          {:routing :failed :redirect-to (bidi/path-for route :build :buildnumber 1)})))

(defn for-build-number [build-number]
  (str "#" (bidi/path-for route :build :buildnumber build-number)))