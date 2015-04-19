(ns lambdacd.steps.git-test
  (:import (java.io File))
  (:require [clojure.test :refer :all]
            [lambdacd.testsupport.matchers :refer :all]
            [lambdacd.steps.git :refer :all]
            [clojure.core.async :as async]
            [clojure.string :as string]
            [lambdacd.util :as util]
            [lambdacd.testsupport.reporter]
            [lambdacd.util :as utils]
            [clojure.java.io :as io]))

(defn- git-commits [cwd]
  (reverse (string/split-lines (:out (util/bash cwd "git log --pretty=format:%H")))))

(defn- git-head-commit [cwd]
  (last (git-commits cwd)))

(defn- create-test-repo []
  (let [dir (util/create-temp-dir)]
    (util/bash dir
                    "git init"
                    "echo \"hello\" > foo"
                    "git add -A"
                    "git commit -m \"some message\""
                    "echo \"world\" > foo"
                    "git add -A"
                    "git commit -m \"some other message\"")
    {:dir dir
     :commits (git-commits dir)
     :repo-name (.getName (File. dir))}))

(defn- commit-to [git-dir]
  (util/bash git-dir
             "echo x >> foo"
             "git add -A"
             "git commit -m \"new commit\"")
  (git-head-commit git-dir))

(defn step-that-returns-the-current-cwd-head [{cwd :cwd} & _]
  {:current-head (git-head-commit cwd)})

(defn- repo-uri-for [git-src-dir]
  (str "file://" git-src-dir))

(defn get-value-or-timeout-from [c]
  (async/go
    (async/<! (async/timeout 60000))
    (async/>! c {:status :timeout :current-revision "timeout"}))
  (async/<!! c))

(defn- ctx-with [last-seen-revision result-channel is-killed]
  {:_pipeline-state (atom { 9 { [42] { :_git-last-seen-revision last-seen-revision }}
                           10 {}})
   :step-id [42]
   :result-channel result-channel
   :is-killed is-killed})

(defn- execute-wait-for-async [git-src-dir last-seen-revision result-channel is-killed]
  (let [ctx (ctx-with last-seen-revision result-channel is-killed)
        ch (async/go (wait-for-git ctx (repo-uri-for git-src-dir) "master"))]
    (Thread/sleep 500) ;; dirty hack to make sure we started waiting before making the next commit
    ch))


(deftest wait-for-git-test
  (testing "that it returns immediately (since it has no last known revision), calls after that wait for the next commit independent of whether the commit occurred before or after starting to wait"
    (let [is-not-killed (atom false)
          result-channel (async/chan 100)
          create-output (create-test-repo)
          git-src-dir (:dir create-output)
          original-head-commit (last (:commits create-output))
          commit-hash-with-nothing-waiting-for-it (commit-to git-src-dir)
          wait-for-commit-that-happend-while-not-waiting-ch (execute-wait-for-async git-src-dir original-head-commit result-channel is-not-killed)
          wait-for-commit-that-happend-while-not-waiting-ch-result  (get-value-or-timeout-from wait-for-commit-that-happend-while-not-waiting-ch)
          wait-started-while-not-having-a-new-commit-ch (execute-wait-for-async git-src-dir commit-hash-with-nothing-waiting-for-it result-channel is-not-killed)
          commit-hash-after-waiting-started-already (commit-to git-src-dir)
          wait-started-while-not-having-a-new-commit-result (get-value-or-timeout-from wait-started-while-not-having-a-new-commit-ch)
          ]
      (is (= commit-hash-with-nothing-waiting-for-it (:revision wait-for-commit-that-happend-while-not-waiting-ch-result)))
      (is (= :success (:status wait-for-commit-that-happend-while-not-waiting-ch-result)))
      (is (= commit-hash-after-waiting-started-already (:revision wait-started-while-not-having-a-new-commit-result)))
      (is (= :success (:status wait-started-while-not-having-a-new-commit-result)))))
  (testing "that when no previous commit is known, we just wait for the next one"
    (let [is-not-killed (atom false)
          result-channel (async/chan 100)
          create-output (create-test-repo)
          git-src-dir (:dir create-output)
          wait-for-ch (execute-wait-for-async git-src-dir nil result-channel is-not-killed)
          new-commit-hash (commit-to git-src-dir)
          wait-for-result (get-value-or-timeout-from wait-for-ch)]
      (is (= new-commit-hash (:revision wait-for-result)))))
  (testing "that it retries until being killed if the repository cannot be reached"
    (let [is-killed (atom false)
          result-ch (execute-wait-for-async "some-uri-that-doesnt-exist" nil (async/chan 10) is-killed)]
      (swap! is-killed (constantly true))
      (is (= :killed (:status (async/<!! result-ch)))))))


(defn some-context
  ([]
    (some-context (utils/create-temp-dir)))
  ([parent]
    {:config { :home-dir parent}
     :step-id [42]
     :result-channel (async/chan 100)
     :is-killed (atom false)}))

;; TODO: replace this test with checkout-and-execute tests, phase out with-git
(deftest with-git-test
  (testing "that it checks out a particular revision and then returns, indicating the location of the checked out repo as :cwd value and that the cloned repo is in a directory named like the repo that was cloned"
    (let [create-output (create-test-repo)
          git-src-dir (:dir create-output)
          commits (:commits create-output)
          repo-name (:repo-name create-output)
          first-commit (first commits)
          with-git-args { :revision first-commit }
          with-git-function (with-git (repo-uri-for git-src-dir) [step-that-returns-the-current-cwd-head])
          with-git-result (with-git-function with-git-args (some-context))]
      (is (= first-commit (:current-head (get (:outputs with-git-result ) [1 42]))))
      (is (.endsWith (:cwd with-git-result) repo-name))
      (is (.startsWith (:out with-git-result) "Cloning"))))
  (testing "that it fails when it couldn't check out a repository"
    (let [with-git-args { :revision "some-commit" }
          with-git-function (with-git "some-unknown-uri" [])
          with-git-result (with-git-function with-git-args (some-context))]
      (is (=  :failure (:status with-git-result)))
      (is (.endsWith (:out with-git-result) "fatal: repository 'some-unknown-uri' does not exist\n" )))))

(defn some-step-that-returns-42 [args ctx]
  {:status :success :the-number 42})
(defn some-step-that-returns-21 [args ctx]
  {:status :success :the-number 21})
(defn some-step-that-returns-the-cwd [{cwd :cwd} _]
  {:status :success :thecwd cwd})

(deftest checkout-and-execute-test
  (let [some-parent-folder (util/create-temp-dir)
        create-output (create-test-repo)
        git-src-dir (:dir create-output)
        repo-uri (repo-uri-for git-src-dir)
        args {}]
    (testing "that it returns the results of the last step it executed"
      (is (map-containing {:the-number 42 } (checkout-and-execute repo-uri "HEAD" args (some-context) [some-step-that-returns-21 some-step-that-returns-42]))))
    (testing "that it returns the results of the last step it executed"
      (is (= some-parent-folder (.getParent (.getParentFile (io/file (:thecwd (checkout-and-execute repo-uri "HEAD" args (some-context some-parent-folder) [some-step-that-returns-the-cwd]))))))))))
