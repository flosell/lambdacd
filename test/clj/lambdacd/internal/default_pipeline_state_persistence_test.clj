(ns lambdacd.internal.default-pipeline-state-persistence-test
  (:use [lambdacd.testsupport.test-util])
  (:require [clojure.test :refer :all]
            [lambdacd.internal.default-pipeline-state-persistence :refer :all]
            [clj-time.core :as t]
            [lambdacd.util :as utils]
            [clojure.java.io :as io]))

(deftest roundtrip-persistence-test
  (testing "the standard case"
    (let [some-pipeline-state {3 {'(0)     {:status :success :most-recent-update-at (t/epoch)}
                                  '(0 1 2) {:status :failure :out "something went wrong"}}}
          home-dir            (utils/create-temp-dir)]
      (write-build-history home-dir 3 some-pipeline-state)
      (is (= some-pipeline-state (read-build-history-from home-dir)))))
  (testing "the standard case in the old world"
    (let [some-pipeline-state {3 {'(0)     {:status :success :most-recent-update-at (t/epoch)}
                                  '(0 1 2) {:status :failure :out "something went wrong"}}}
          home-dir            (utils/create-temp-dir)]
      (write-build-history-internal home-dir 3 some-pipeline-state :json)
      (is (= some-pipeline-state (read-build-history-from-internal home-dir :json)))))
  (testing "that string-keys in a step result are suppored as well (#101)"
    (let [some-pipeline-state {3 {'(0) {:status :success :_git-last-seen-revisions {"refs/heads/master" "some-sha"}}}}
          home-dir            (utils/create-temp-dir)]
      (write-build-history home-dir 3 some-pipeline-state)
      (is (= some-pipeline-state (read-build-history-from home-dir)))))
  (testing "that keyworded values in a step result are suppored as well (#101)"
    (let [some-pipeline-state {3 {'(0) {:status :success :v :x}}}
          home-dir            (utils/create-temp-dir)]
      (write-build-history home-dir 3 some-pipeline-state)
      (is (= some-pipeline-state (read-build-history-from home-dir)))))
  (testing "cleaning up old history"
    (testing "that build-directories will be deleted if they no longer exist in the build-state"
      (let [some-pipeline-state {0 {'(0) {:status :success}}
                                 1 {'(0) {:status :success}}
                                 2 {'(0) {:status :success}}}
            truncated-pipeline-state {1 {'(0) {:status :success}}
                                      2 {'(0) {:status :success}}}
            home-dir            (utils/create-temp-dir)]
        (doall (for [build (range 0 3)]
                 (write-build-history home-dir build some-pipeline-state)))
        (doall (for [build (range 0 3)]
                 (is (.exists (io/file home-dir (str "build-" build))))))

        (is (.exists (io/file home-dir "build-0")))
        (is (.exists (io/file home-dir "build-1")))
        (is (.exists (io/file home-dir "build-2")))
        (clean-up-old-history home-dir truncated-pipeline-state)

        (is (not (.exists (io/file home-dir "build-0"))))
        (is (.exists (io/file home-dir "build-1")))
        (is (.exists (io/file home-dir "build-2")))))
    (testing "that it does not clean up things other than build directories"
      (let [truncated-pipeline-state {}
            home-dir            (utils/create-temp-dir)]
        (.mkdirs (io/file home-dir "helloworld"))
        (is (.exists (io/file home-dir "helloworld")))
        (clean-up-old-history home-dir truncated-pipeline-state)
        (is (.exists (io/file home-dir "helloworld"))))))
  (testing "backwards compatibility"
    (testing "that old json is read if no edn file exists"
      (let [some-pipeline-state {3 {'(0) {:status :success :source "json"}}}
            home-dir            (utils/create-temp-dir)]
        (write-build-history-internal home-dir 3 some-pipeline-state :json)
        (is (= some-pipeline-state (read-build-history-from home-dir)))))
    (testing "that edn is preferred if json and edn exist"
      (let [json-state {3 {'(0) {:status :success :source "json"}}}
            edn-state  {3 {'(0) {:status :success :source "edn"}}}
            home-dir   (utils/create-temp-dir)]
        (write-build-history-internal home-dir 3 json-state :json)
        (write-build-history-internal home-dir 3 edn-state :edn)
        (is (= edn-state (read-build-history-from home-dir)))))
    (testing "that both json and edn are written so that users can still return to earlier versions without losing history"
      (let [some-pipeline-state {3 {'(0) {:status :success :source "json"}}}
            home-dir            (utils/create-temp-dir)]
        (write-build-history home-dir 3 some-pipeline-state)
        (is (= some-pipeline-state (read-build-history-from home-dir)))
        (is (= some-pipeline-state (read-build-history-from-internal home-dir :json)))))))

(defn- roundtrip-date-time [data]
  (dates->clj-times
    (clj-times->dates data)))

(deftest date-time-roundtrip-test
  (testing "a simple case"
    (let [data {:now (t/now)}]
      (is (= data (roundtrip-date-time data)))))
  (testing "vectors"
    (let [data [(t/now)]]
      (is (= data (roundtrip-date-time data)))))
  (testing "nested maps"
    (let [data {:now (t/now)
                :other-times {"epoch" (t/epoch)}}]
      (is (= data (roundtrip-date-time data))))))
