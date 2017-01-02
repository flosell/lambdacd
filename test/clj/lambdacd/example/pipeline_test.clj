(ns lambdacd.example.steps-test
  (:require [clojure.test :refer [deftest testing is]]
            [conjure.core :as c]
            [lambdacd.steps.git :as git]
            [todopipeline.steps :as steps]
            [todopipeline.pipeline :as pipeline]
            [lambdacd.steps.shell :as shell]
            [lambdacd.execution.core :as execution]
            [lambdacd.testsupport.data :refer [some-ctx]]))

(deftest todopipeline-test
  (testing "a successful pipeline run" ; this isn't a particularly interesting test but it's a start
    (c/stubbing [git/wait-with-details {:status :success}
                 shell/bash {:status :success
                             :exit 0}]
                (is (= :success (:status (execution/run-pipeline pipeline/pipeline-def (some-ctx)))))
                (is (c/verify-called-once-with-args shell/bash )))))
