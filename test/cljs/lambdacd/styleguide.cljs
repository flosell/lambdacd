(ns lambdacd.styleguide
  (:require [lambdacd.testutils :refer [query]]
            [lambdacd.testcases :as testcases]
            [reagent.core :as reagent]
            [re-frame.core :as re-frame]))

(defn render [component]
  (reagent/render-component component (.getElementById js/document "content")))

(defn- testcase [query]
  (second (re-find #"testcase=([^&]+)" query)))

(defn- initialize-styleguide-overview []
  (println "overview"))

(defn- initialize-testcase [testcase-id]
  (let [testcases-by-id (group-by :id testcases/tc)
        testcase        (first (get testcases-by-id testcase-id))
        component       (:component testcase)
        data            (:data      testcase)]
    (if data
      (with-redefs [re-frame/subscribe data]
                   (render component)))
    (render component)))

(defn initialize-styleguide []
  (let [testcase (testcase (query))]
    (if testcase
      (initialize-testcase testcase)
      (initialize-styleguide-overview))))