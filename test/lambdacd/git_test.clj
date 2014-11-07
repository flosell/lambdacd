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


(deftest wait-for-git-test
  (testing "that is polls the git repository until a version that is different from the current one is found"
    (let [create-output (create-test-repo)
          git-src-dir (:dir create-output)
          wait-channel (let [ch (async/go (wait-for-git (repo-uri-for git-src-dir) "master"))]
                         (Thread/sleep 500) ;; dirty hack to make sure we started waiting before making the next commit
                         ch)
          new-commit-hash (commit-to git-src-dir)
          wait-result  (get-value-or-timeout-from wait-channel)]
      (is (= new-commit-hash (:current-revision wait-result)))
      (is (= :success (:status wait-result))))))


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
