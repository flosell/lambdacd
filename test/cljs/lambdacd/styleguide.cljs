(ns lambdacd.styleguide
  (:require [lambdacd.testutils :refer [query]]
            [lambdacd.testcases :as testcases]
            [reagent.core :as reagent]))

(defn render [component]
  (reagent/render-component component (.getElementById js/document "content")))

(defn- testcase [query]
  (second (re-find #"testcase=([^&]+)" query)))

(defn- initialize-styleguide-overview []
  (println "overview"))

(defn- initialize-testcase [testcase]
  (case testcase
    "main" (render [#'testcases/main])
    "main-connection-lost" (render [#'testcases/main-connection-lost])
    "normal-pipeline" (render [#'testcases/normal-pipeline])
    "normal-history" (render [#'testcases/normal-history])))

(defn initialize-styleguide []
  (let [testcase (testcase (query))]
    (if testcase
      (initialize-testcase testcase)
      (initialize-styleguide-overview))))