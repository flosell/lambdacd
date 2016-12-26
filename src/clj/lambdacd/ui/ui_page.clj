(ns lambdacd.ui.ui-page
  (:require [hiccup.core :as h]
            [hiccup.page :as p]
            [hiccup.element :as e]
            [lambdacd.util :as utils]
            [lambdacd.ui.internal.util :as ui-utils]))

(defn css-includes []
  (list
    (p/include-css "css/thirdparty/normalize.css")
    (p/include-css "css/main.css")
    (p/include-css "css/thirdparty/font-awesome-4.4.0/css/font-awesome.min.css")))

(defn js-includes []
  (p/include-js "js-gen/app.js"))

(defn favicon []
  (list
    [:link {:rel "icon" :type "image/png" :sizes "32x32" :href "favicon-32x32.png"}]
    [:link {:rel "icon" :type "image/png" :sizes "96x96" :href "favicon-96x96.png"}]
    [:link {:rel "icon" :type "image/png" :sizes "16x16" :href "favicon-16x16.png"}]))

(defn app-placeholder []
  [:div {:id "app"}])

; -----------------------------------------------------------------------------

(defn title [pipeline-name]
  (if pipeline-name
    [:title (str pipeline-name " - LambdaCD")]
    [:title "LambdaCD"]))

(defn header [pipeline-name]
  [:div {:class "app__header"}
   [:a {:href "/"}
    [:h1 {:class "app__header__lambdacd"} "LambdaCD"]]
   (if pipeline-name
     [:span {:class "app__header__pipeline-name"} pipeline-name])])

(defn ui-config [ui-config]
  (e/javascript-tag
    (str "lambdacd_ui_config=" (ui-utils/to-json
                                 (or ui-config {})))))

(defn ui-page [pipeline]
  (let [pipeline-name  (get-in pipeline [:context :config :name])
        ui-config-data (get-in pipeline [:context :config :ui-config])]
    (h/html
      [:html
       [:head
        (title pipeline-name)
        (favicon)
        (css-includes)
        (ui-config ui-config-data)]
       [:body
        [:div {:class "app l-horizontal"}
         (header pipeline-name)
         (app-placeholder)]
        (js-includes)]])))
