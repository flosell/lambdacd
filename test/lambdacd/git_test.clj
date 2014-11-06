(ns lambdacd.git-test
  (:require [clojure.test :refer :all]
            [lambdacd.git :refer :all]
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

(defn step-that-returns-the-current-cwd-head [{cwd :cwd} & _]
  {:current-head (git-head-commit cwd)})

(deftest with-git-test
  (testing "that it checks out a particular revision and then returns, indicating the location of the checked out repo as :cwd value"
    (let [create-output (create-test-repo)
          git-src-dir (:dir create-output)
          commits (:commits create-output)
          first-commit (first commits)
          with-git-args { :revision first-commit }
          with-git-function (with-git (str "file://" git-src-dir) [step-that-returns-the-current-cwd-head])
          with-git-result (with-git-function with-git-args {:step-id [42]})]
      (is (= first-commit (:current-head (get (:outputs with-git-result ) [1 42])))))))
