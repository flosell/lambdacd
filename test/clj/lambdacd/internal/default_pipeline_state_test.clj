(ns lambdacd.internal.default-pipeline-state-test
  (:use [lambdacd.testsupport.test-util])
  (:require [clojure.test :refer :all]
            [lambdacd.internal.default-pipeline-state :refer :all]
            [lambdacd.internal.pipeline-state :refer [next-build-number] :as pipeline-state-record]
            [lambdacd.util :as utils]
            [clj-time.core :as t]
            [lambdacd.testsupport.test-util :as tu]
            [lambdacd.testsupport.data :refer [some-ctx-with some-ctx]]
            [clojure.java.io :as io]
            [clojure.edn :as edn]
            [lambdacd.state.protocols :as protocols])
  (:import (java.util Date)))

(def no-home-dir nil)
(def keep-all-builds Integer/MAX_VALUE)

(defn- after-update [build id newstate]
  (let [state (->DefaultPipelineState (atom clean-pipeline-state) (atom {}) no-home-dir keep-all-builds)]
    (pipeline-state-record/update state build id newstate)
    (pipeline-state-record/get-all state)))

(deftest general-pipeline-state-test
  (testing "that the next buildnumber is the highest build-number currently in the pipeline-state"
    (is (= 5 (next-build-number (->DefaultPipelineState (atom { 3 {} 4 {} 1 {}}) (atom {}) no-home-dir keep-all-builds))))
    (is (= 1 (next-build-number (->DefaultPipelineState (atom clean-pipeline-state) (atom {}) no-home-dir keep-all-builds)))))
  (testing "that a new pipeline-state will be set on update"
    (is (= { 10 { [0] { :foo :bar }}} (tu/without-ts (after-update 10 [0] {:foo :bar})))))
  (testing "that update will set a first-updated-at and most-recent-update-at timestamp"
    (let [first-update-timestamp (t/minus (t/now) (t/minutes 1))
          last-updated-timestamp (t/now)
          state                  (->DefaultPipelineState (atom clean-pipeline-state) (atom {}) no-home-dir keep-all-builds)]
      (t/do-at first-update-timestamp (pipeline-state-record/update state 10 [0] {:foo :bar}))
      (t/do-at last-updated-timestamp (pipeline-state-record/update state 10 [0] {:foo :baz}))
      (is (= {10 {[0] {:foo :baz :most-recent-update-at last-updated-timestamp :first-updated-at first-update-timestamp}}}
             (pipeline-state-record/get-all state)))))
  (testing "that updating will save the current state to the file-system"
    (let [home-dir    (utils/create-temp-dir)
          step-result {:foo :bar}
          state       (->DefaultPipelineState (atom clean-pipeline-state) (atom {}) home-dir keep-all-builds)]
      (t/do-at (t/epoch) (pipeline-state-record/update state 10 [0] step-result))
      (is (= [{:step-id     "0"
               :step-result {:foo                   :bar
                             :most-recent-update-at (Date. 0)
                             :first-updated-at      (Date. 0)}}]
             (edn/read-string (slurp (str home-dir "/build-10/build-state.edn")))))))
  (testing "truncate"
    (testing "that updating will remove the oldest build if more than the maximum number of builds are written"
      (let [max-builds 5
            state      (->DefaultPipelineState (atom clean-pipeline-state) (atom {}) no-home-dir max-builds)]
        (doall (for [build (range 5)]
                 (pipeline-state-record/update state build [0] {:foo :baz :build build})))
        (is (= {[0] {:foo :baz :build 0}} (tu/without-ts (get (pipeline-state-record/get-all state) 0))))
        (is (= {[0] {:foo :baz :build 4}} (tu/without-ts (get (pipeline-state-record/get-all state) 4))))

        (pipeline-state-record/update state 5 [0] {:foo :baz :build 5})

        (is (nil?  (tu/without-ts (get (pipeline-state-record/get-all state) 0))))
        (is (= {[0] {:foo :baz :build 4}} (tu/without-ts (get (pipeline-state-record/get-all state) 4))))
        (is (= {[0] {:foo :baz :build 5}} (tu/without-ts (get (pipeline-state-record/get-all state) 5))))))
    (testing "that updating will remove outdated state from file-system"
      (let [max-builds 5
            home-dir    (utils/create-temp-dir)
            state      (->DefaultPipelineState (atom clean-pipeline-state) (atom {}) home-dir max-builds)]
        (doall (for [build (range 5)]
                 (pipeline-state-record/update state build [0] {:foo :baz :build build})))
        (is (.exists (io/file home-dir "build-0/build-state.edn")))
        (is (.exists (io/file home-dir "build-4/build-state.edn")))
        (is (not (.exists (io/file home-dir "build-5/build-state.edn"))))

        (pipeline-state-record/update state 5 [0] {:foo :baz :build 5})

        (is (not (.exists (io/file home-dir "build-0/build-state.edn"))))
        (is (.exists (io/file home-dir "build-4/build-state.edn")))
        (is (.exists (io/file home-dir "build-5/build-state.edn")))))
    (testing "that it will not accidently remove pipeline-structures for builds that arent truncated but don't have a state yet"
      (let [max-builds              10
            home-dir                (utils/create-temp-dir)
            state                   (->DefaultPipelineState (atom clean-pipeline-state) (atom {}) home-dir max-builds)
            build-dir (io/file home-dir "build-6")
            pipeline-structure-file (io/file build-dir "pipeine-structure.edn")]
        (doall (for [build (range 5)]
                 (pipeline-state-record/update state build [0] {:foo :baz :build build})))
        (.mkdirs build-dir)
        (spit pipeline-structure-file "test")
        (is (.exists pipeline-structure-file))

        (pipeline-state-record/update state 5 [0] {:foo :baz :build 5})

        (is (.exists pipeline-structure-file))))))

(deftest pipeline-structure-state-test
  (testing "that we can consume and retrieve pipeline-structure"
    (let [component (new-default-pipeline-state {:home-dir (utils/create-temp-dir)})]
      (protocols/consume-pipeline-structure component 1 {:some-pipeline :structure})
      (is (= {:some-pipeline :structure} (protocols/get-pipeline-structure component 1)))))
  (testing "that we return nil if pipeline-structure isn't there"
    (let [component (new-default-pipeline-state {:home-dir (utils/create-temp-dir)})]
      (is (= nil (protocols/get-pipeline-structure component 43)))))
  (testing "that we can retrieve the pipeline-structure from disk"
    (let [home      (utils/create-temp-dir)
          component (new-default-pipeline-state {:home-dir home})]
      (protocols/consume-pipeline-structure component 1 {:some-pipeline :structure})
      (let [other-component (new-default-pipeline-state {:home-dir home})]
        (is (= {:some-pipeline :structure} (protocols/get-pipeline-structure other-component 1)))))))
