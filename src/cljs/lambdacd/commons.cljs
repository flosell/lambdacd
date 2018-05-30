(ns lambdacd.commons)

(defn loading-screen []
  [:div {:key "loading-screen" :class "app__loading-screen"}
   [:span "Loading..."]])
