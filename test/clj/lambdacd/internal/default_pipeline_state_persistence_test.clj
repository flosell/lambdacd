(ns lambdacd.internal.default-pipeline-state-persistence-test
  (:use [lambdacd.testsupport.test-util])
  (:require [clojure.test :refer :all]
            [lambdacd.internal.default-pipeline-state-persistence :refer :all]
            [lambdacd.presentation.pipeline-structure-test :as pipeline-structure-test]
            [clj-time.core :as t]
            [lambdacd.util :as utils]
            [lambdacd.util.internal.temp :as temp-util]
            [clojure.java.io :as io]))

(deftest roundtrip-persistence-test
  (testing "pipeline-state"
    (testing "the standard case"
      (let [some-pipeline-state {3 {'(0)     {:status :success :most-recent-update-at (t/epoch)}
                                    '(0 1 2) {:status :failure :out "something went wrong"}}}
            home-dir            (temp-util/create-temp-dir)]
        (write-build-history home-dir 3 some-pipeline-state)
        (is (= some-pipeline-state (read-build-history-from home-dir)))))
    (testing "that string-keys in a step result are supported as well (#101)"
      (let [some-pipeline-state {3 {'(0) {:status :success :_git-last-seen-revisions {"refs/heads/master" "some-sha"}}}}
            home-dir            (temp-util/create-temp-dir)]
        (write-build-history home-dir 3 some-pipeline-state)
        (is (= some-pipeline-state (read-build-history-from home-dir)))))
    (testing "that keyworded values in a step result are suppored as well (#101)"
      (let [some-pipeline-state {3 {'(0) {:status :success :v :x}}}
            home-dir            (temp-util/create-temp-dir)]
        (write-build-history home-dir 3 some-pipeline-state)
        (is (= some-pipeline-state (read-build-history-from home-dir))))))
  (testing "generic build-data"
    (testing "the standard case"
      (let [home-dir                (temp-util/create-temp-dir)
            some-pipeline-structure pipeline-structure-test/foo-pipeline-display-representation]
        (write-build-data-edn home-dir 1 some-pipeline-structure "pipeline-structure.edn")
        (write-build-data-edn home-dir 2 some-pipeline-structure "pipeline-structure.edn")
        (is (= some-pipeline-structure (get (read-build-datas home-dir "pipeline-structure.edn") 1)))
        (is (= some-pipeline-structure (get (read-build-datas home-dir "pipeline-structure.edn") 2)))))))

(deftest clean-up-old-builds-test
  (testing "cleaning up old history"
    (testing "that given build-directories will be deleted"
      (let [some-pipeline-state      {0 {'(0) {:status :success}}
                                      1 {'(0) {:status :success}}
                                      2 {'(0) {:status :success}}}
            home-dir                 (temp-util/create-temp-dir)]
        (doall (for [build (range 0 3)]
                 (write-build-history home-dir build some-pipeline-state)))
        (doall (for [build (range 0 3)]
                 (is (.exists (io/file home-dir (str "build-" build))))))

        (is (.exists (io/file home-dir "build-0")))
        (is (.exists (io/file home-dir "build-1")))
        (is (.exists (io/file home-dir "build-2")))
        (clean-up-old-builds home-dir [0])

        (is (not (.exists (io/file home-dir "build-0"))))
        (is (.exists (io/file home-dir "build-1")))
        (is (.exists (io/file home-dir "build-2")))))
    (testing "that it does not clean up things other than build directories"
      (let [home-dir (temp-util/create-temp-dir)]
        (.mkdirs (io/file home-dir "helloworld"))
        (is (.exists (io/file home-dir "helloworld")))
        (clean-up-old-builds home-dir [0])
        (is (.exists (io/file home-dir "helloworld")))))))

(deftest read-build-history-from-test ; covers only edge-cases that aren't coverd by roundtrip
  (testing "that it will return an empty history if no state has been written yet"
    (let [home-dir (temp-util/create-temp-dir)]
      (is (= {} (read-build-history-from home-dir)))))
  (testing "that it ignores build directories with no build state (e.g. because only structure has been written yet"
    (let [home-dir (temp-util/create-temp-dir)]
      (.mkdirs (io/file home-dir "build-1"))
      (is (= {} (read-build-history-from home-dir))))))

(deftest read-pipeline-datas-test ; covers only edge-cases that aren't covered by roundtrip
  (testing "that it will return an empty data if no state has been written yet"
    (let [home-dir (temp-util/create-temp-dir)]
      (is (= {} (read-build-datas home-dir "pipeline-structure.edn")))))
  (testing "that it adds a fallback-marker for build directories with no pipeline structure (e.g. because they were created before this feature was available)"
    (let [home-dir (temp-util/create-temp-dir)]
      (.mkdirs (io/file home-dir "build-1"))
      (is (= {1 :fallback} (read-build-datas home-dir "pipeline-structure.edn"))))))

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
