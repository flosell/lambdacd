(ns lambdacd.git-test
  (:require [clojure.test :refer :all]
            [lambdacd.git :refer :all]
            [clojure.core.async :as async]
            [clojure.string :as string]
            [lambdacd.shell :as sh]
            [lambdacd.util :as util]
            [lambdacd.test-util :as test-util]))

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
    { :dir dir :commits (git-commits dir) }))

(defn- create-config []
  { :home-dir (util/create-temp-dir)})

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

(defn- execute-wait-for-async [git-src-dir config]
  (let [ch (async/go (wait-for-git { :config config } (repo-uri-for git-src-dir) "master"))]
    (Thread/sleep 500) ;; dirty hack to make sure we started waiting before making the next commit
    ch))

(deftest wait-for-git-test
  (testing "that it returns immediately (since it has no last known revision), calls after that wait for the next commit independent of whether the commit occurred before or after starting to wait"
    (let [config (create-config)
          create-output (create-test-repo)
          git-src-dir (:dir create-output)
          original-head-commit (last (:commits create-output))
          wait-for-original-commit-ch (execute-wait-for-async git-src-dir config)
          wait-for-original-commit-result  (get-value-or-timeout-from wait-for-original-commit-ch)
          commit-hash-with-nothing-waiting-for-it (commit-to git-src-dir)
          wait-for-commit-that-happend-while-not-waiting-ch (execute-wait-for-async git-src-dir config)
          wait-for-commit-that-happend-while-not-waiting-ch-result  (get-value-or-timeout-from wait-for-commit-that-happend-while-not-waiting-ch)
          wait-started-while-not-having-a-new-commit-ch (execute-wait-for-async git-src-dir config)
          commit-hash-after-waiting-started-already (commit-to git-src-dir)
          wait-started-while-not-having-a-new-commit-result (get-value-or-timeout-from wait-started-while-not-having-a-new-commit-ch)
          ]
      (is (= original-head-commit (:current-revision wait-for-original-commit-result)))
      (is (= :success (:status wait-for-original-commit-result)))
      (is (= commit-hash-with-nothing-waiting-for-it (:current-revision wait-for-commit-that-happend-while-not-waiting-ch-result)))
      (is (= :success (:status wait-for-commit-that-happend-while-not-waiting-ch-result)))
      (is (= commit-hash-after-waiting-started-already (:current-revision wait-started-while-not-having-a-new-commit-result)))
      (is (= :success (:status wait-started-while-not-having-a-new-commit-result)))
      )))


(deftest with-git-test
  (testing "that it checks out a particular revision and then returns, indicating the location of the checked out repo as :cwd value"
    (let [create-output (create-test-repo)
          git-src-dir (:dir create-output)
          commits (:commits create-output)
          first-commit (first commits)
          with-git-args { :revision first-commit }
          with-git-function (with-git (repo-uri-for git-src-dir) [step-that-returns-the-current-cwd-head])
          with-git-result (with-git-function with-git-args {:step-id [42]})]
      (is (= first-commit (:current-head (get (:outputs with-git-result ) [1 42])))))))
