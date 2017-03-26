(ns lambdacd.ui.ui-server
  "Deprecated, use `lambdacd.ui.core` instead."
  {:deprecated "0.13.1"}
  (:require [lambdacd.ui.core :as ui-core]))

(defn ui-for
  "Deprecated, use `lambdacd.ui.core/ui-for` instead."
  {:deprecated "0.13.1"}
  [pipeline]
  (ui-core/ui-for pipeline))
