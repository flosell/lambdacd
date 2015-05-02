(ns lambdacd.testsupport.data
  (:require [lambdacd.util :as utils]
            [clojure.core.async :as async]))


(def some-ctx
   {:_pipeline-state (atom {})
    :step-id         [42]
    :result-channel  (async/chan (async/dropping-buffer 100))
    :config          {:home-dir (utils/create-temp-dir)}
    :is-killed       (atom false)})

(defn some-ctx-with [& args]
  (apply assoc some-ctx args))