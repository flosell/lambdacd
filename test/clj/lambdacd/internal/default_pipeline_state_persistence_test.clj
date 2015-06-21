(ns lambdacd.internal.default-pipeline-state-persistence-test
  (:use [lambdacd.testsupport.test-util])
  (:require [clojure.test :refer :all]
            [lambdacd.internal.default-pipeline-state-persistence :refer :all]
            [clj-time.core :as t]
            [lambdacd.util :as utils]))

(deftest roundtrip-persistence-test
  (let [some-pipeline-state { 3 {'(0)     {:status :success :most-recent-update-at (t/epoch)}
                                 '(0 1 2) {:status :failure :out "something went wrong"}}}
        home-dir (utils/create-temp-dir)]
    (write-build-history home-dir 3 some-pipeline-state)
    (is (= some-pipeline-state (read-build-history-from home-dir)))))