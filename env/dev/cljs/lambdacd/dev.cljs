(ns ^:figwheel-no-load lambdacd.dev
  (:require [lambdacd.ui-core :as core]
            [figwheel.client :as figwheel :include-macros true]
            [weasel.repl :as weasel]
            [lambdacd.testutils :refer [path]]
            [lambdacd.styleguide :as styleguide]
            [reagent.core :as r]))

(enable-console-print!)

(figwheel/watch-and-reload
  :websocket-url "ws://localhost:3449/figwheel-ws"
  :jsload-callback (fn []
                     (r/force-update-all)))

(defn- contains [s substr]
  (not= -1 (.indexOf s substr)))

(defn initialize-app []
  (core/init!))

(if (contains (path) "styleguide")
  (styleguide/initialize-styleguide)
  (initialize-app))

