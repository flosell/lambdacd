(ns lambdacd.example.steps-test
  (:require [clojure.test :refer [deftest testing is]]
            [conjure.core :as c]
            [lambdacd.steps.git :as git]
            [todopipeline.steps :as steps]))

; testing a simple build step
(deftest wait-for-frontend-repo-test
  (testing "that it returns the most recent frontend sha and head for the backend"
    (c/stubbing [git/wait-with-details {:revision "some-revision"
                                        :status :success}]
                (is (= {:status :success
                        :frontend-revision "some-revision"
                        :backend-revision "HEAD"
                        :revision "some-revision"}
                       (steps/wait-for-frontend-repo nil nil))))))