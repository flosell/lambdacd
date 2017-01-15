(ns lambdacd.internal.default-pipeline-state-test
  (:use [lambdacd.testsupport.test-util])
  (:require [clojure.test :refer :all]
            [lambdacd.internal.default-pipeline-state :refer [new-default-pipeline-state]]
            [lambdacd.util :as utils]
            [lambdacd.util.internal.temp :as temp-util]
            [clj-time.core :as t]
            [lambdacd.testsupport.test-util :as tu]
            [lambdacd.testsupport.data :refer [some-ctx-with some-ctx]]
            [clojure.java.io :as io]
            [lambdacd.state.protocols :as protocols]))

(deftest next-build-number-test
  (testing "that the next buildnumber is the highest build-number currently in the pipeline-state"
    (is (= 1 (protocols/next-build-number (new-default-pipeline-state {:home-dir (temp-util/create-temp-dir)}))))
    (is (= 5 (protocols/next-build-number (new-default-pipeline-state {:home-dir (temp-util/create-temp-dir)}
                                                                      :initial-state-for-testing {3 {} 4 {} 1 {}}))))))

(deftest all-build-numbers-test
  (testing "that we can get a list of all build numbers"
    (is (= [] (protocols/all-build-numbers (new-default-pipeline-state {:home-dir (temp-util/create-temp-dir)}))))
    (is (= [1 3 4] (protocols/all-build-numbers (new-default-pipeline-state {:home-dir (temp-util/create-temp-dir)}
                                                                            :initial-state-for-testing (sorted-map-by > 3 {} 4 {} 1 {})))))))

(deftest pipeline-structure-state-test
  (testing "that we can consume and retrieve pipeline-structure"
    (let [component (new-default-pipeline-state {:home-dir (temp-util/create-temp-dir)})]
      (protocols/consume-pipeline-structure component 1 {:some-pipeline :structure})
      (is (= {:some-pipeline :structure} (protocols/get-pipeline-structure component 1)))))
  (testing "that we return nil if pipeline-structure isn't there"
    (let [component (new-default-pipeline-state {:home-dir (temp-util/create-temp-dir)})]
      (is (= nil (protocols/get-pipeline-structure component 43)))))
  (testing "that we can retrieve the pipeline-structure from disk"
    (let [home      (temp-util/create-temp-dir)
          component (new-default-pipeline-state {:home-dir home})]
      (protocols/consume-pipeline-structure component 1 {:some-pipeline :structure})
      (let [other-component (new-default-pipeline-state {:home-dir home})]
        (is (= {:some-pipeline :structure} (protocols/get-pipeline-structure other-component 1)))))))

(deftest build-metadata-test
  (testing "that we can consume and retrieve build metadata"
    (let [component (new-default-pipeline-state {:home-dir (temp-util/create-temp-dir)})]
      (protocols/consume-build-metadata component 1 {:some :metadata})
      (is (= {:some :metadata} (protocols/get-build-metadata component 1)))))
  (testing "that we can retrieve the build-metadata from disk"
    (let [home-dir  (temp-util/create-temp-dir)
          component (new-default-pipeline-state {:home-dir home-dir})]
      (protocols/consume-build-metadata component 1 {:some :metadata})
      (let [other-component (new-default-pipeline-state {:home-dir home-dir})]
        (is (= {:some :metadata} (tu/without-ts (protocols/get-build-metadata other-component 1))))))))

(deftest step-result-state-test
  (testing "that we can consume and retrieve step results"
    (let [component (new-default-pipeline-state {:home-dir (temp-util/create-temp-dir)})]
      (protocols/consume-step-result-update component 1 [1 1] {:some :step-result})
      (is (= {[1 1] {:some :step-result}} (tu/without-ts (protocols/get-step-results component 1))))))(testing "that update will set a first-updated-at and most-recent-update-at timestamp"
    (let [first-update-timestamp (t/minus (t/now) (t/minutes 1))
          last-updated-timestamp (t/now)
          component (new-default-pipeline-state {:home-dir (temp-util/create-temp-dir)})]
      (t/do-at first-update-timestamp (protocols/consume-step-result-update component 1 [1 1] {:some :step-result}))
      (t/do-at last-updated-timestamp (protocols/consume-step-result-update component 1 [1 1] {:some :step-result}))
      (is (= {[1 1] {:some :step-result :most-recent-update-at last-updated-timestamp :first-updated-at first-update-timestamp}} (protocols/get-step-results component 1)))))
  (testing "truncating"
    (testing "that updating will remove the oldest build if more than the maximum number of builds are written"
      (let [max-builds 5
            component  (new-default-pipeline-state {:home-dir   (temp-util/create-temp-dir)
                                                    :max-builds max-builds})]
        (doall (for [build (range max-builds)]
                 (protocols/consume-step-result-update component build [1 1] {:foo :bar :build build})))

        (is (= {[1 1] {:foo :bar :build 0}} (tu/without-ts (protocols/get-step-results component 0))))
        (is (= {[1 1] {:foo :bar :build 4}} (tu/without-ts (protocols/get-step-results component 4))))

        (protocols/consume-step-result-update component 5 [1 1] {:foo :bar :build 5})

        (is (= nil (protocols/get-step-results component 0)))
        (is (= {[1 1] {:foo :bar :build 4}} (tu/without-ts (protocols/get-step-results component 4))))
        (is (= {[1 1] {:foo :bar :build 5}} (tu/without-ts (protocols/get-step-results component 5))))))
    (testing "that updating will remove outdated state from file-system"
      (let [max-builds 5
            home-dir      (temp-util/create-temp-dir)
            component  (new-default-pipeline-state {:home-dir   home-dir
                                                    :max-builds max-builds})]
        (doall (for [build (range max-builds)]
                 (protocols/consume-step-result-update component build [1 1] {:foo :bar :build build})))

        (is (.exists (io/file home-dir "build-0/build-state.edn")))
        (is (.exists (io/file home-dir "build-4/build-state.edn")))
        (is (not (.exists (io/file home-dir "build-5/build-state.edn"))))

        (protocols/consume-step-result-update component 5 [1 1] {:foo :bar :build 5})

        (is (not (.exists (io/file home-dir "build-0/build-state.edn"))))
        (is (.exists (io/file home-dir "build-4/build-state.edn")))
        (is (.exists (io/file home-dir "build-5/build-state.edn")))))
    (testing "that it will not accidently remove pipeline-structures for builds that arent truncated but don't have a state yet"
      (let [max-builds              10
            home-dir                (temp-util/create-temp-dir)
            state                   (new-default-pipeline-state {:home-dir   home-dir
                                                                 :max-builds max-builds})
            build-dir (io/file home-dir "build-6")
            pipeline-structure-file (io/file build-dir "pipeine-structure.edn")]
        (doall (for [build (range max-builds)]
                 (protocols/consume-step-result-update state build [0] {:foo :baz :build build})))
        (.mkdirs build-dir)
        (spit pipeline-structure-file "test")
        (is (.exists pipeline-structure-file))

        (protocols/consume-step-result-update state 5 [0] {:foo :baz :build 5})

        (is (.exists pipeline-structure-file)))))
  (testing "that we can retrieve step-results from disk"
    (let [home-dir     (temp-util/create-temp-dir)
          component (new-default-pipeline-state {:home-dir home-dir})]
      (protocols/consume-step-result-update component 1 [1 1] {:some :step-result})
      (let [other-component (new-default-pipeline-state {:home-dir home-dir})]
        (is (= {[1 1] {:some :step-result}} (tu/without-ts (protocols/get-step-results other-component 1))))))))
