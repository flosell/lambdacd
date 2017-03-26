(ns lambdacd.steps.git-test
  (:import (java.io File))
  (:require [clojure.test :refer :all]
            [lambdacd.testsupport.matchers :refer :all]
            [lambdacd.testsupport.test-util :refer :all]
            [lambdacd.steps.git :refer :all]
            [clojure.core.async :as async]
            [clojure.string :as string]
            [lambdacd.util :as util]
            [lambdacd.testsupport.reporter]
            [lambdacd.util.internal.bash :as bash-util]
            [lambdacd.testsupport.data :refer [some-ctx some-ctx-with]]
            [clojure.java.io :as io]
            [lambdacd.testsupport.test-util :as tu]
            [lambdacd.util.internal.temp :as temp-util]
            [lambdacd.stepsupport.killable :as killable]))

(defn- git-commits [cwd]
  (reverse (string/split-lines (:out (bash-util/bash cwd "git log --pretty=format:%H")))))

(defn- git-head-commit [cwd]
  (last (git-commits cwd)))

(defn- create-test-repo []
  (let [dir (temp-util/create-temp-dir)]
    (bash-util/bash dir
                    "git init"
                    "echo \"hello\" > foo"
                    "git add -A"
                    "git commit -m \"some message\""
                    "echo \"world\" > foo"
                    "git add -A"
                    "git commit -m \"some other message\"")
    {:dir dir
     :commits (git-commits dir)}))

(defn create-test-repo-with-branch []
  (let [dir (temp-util/create-temp-dir)]
    (bash-util/bash dir
               "git init"
               "echo \"hello\" > foo"
               "git add -A"
               "git commit -m \"some message\""
               "git branch develop"
               "git checkout develop"
               "echo \"world\" > bar"
               "git add -A"
               "git commit -m \"some other message\"")
    {:dir dir
     :commits (git-commits dir)}))

(defn- commit-to
  ([git-dir]
   (commit-to git-dir "new commit"))
  ([git-dir commit-msg]
    (bash-util/bash git-dir
               "echo x >> foo"
               "git add -A"
               (str "git commit -m \"" commit-msg "\""))
    (git-head-commit git-dir)))

(defn step-that-returns-the-current-cwd-head [{cwd :cwd} & _]
  {:current-head (git-head-commit cwd)
   :status :success})

(defn- repo-uri-for [git-src-dir]
  (str "file://" git-src-dir))

(defn get-value-or-timeout-from [c]
  (get-or-timeout c :timeout 60000))

(defn- some-context-with [last-seen-revision result-channel is-killed]
 (some-ctx-with
   :initial-pipeline-state { 9 { [42] { :_git-last-seen-revision last-seen-revision }}
                             10 {}}
   :result-channel result-channel
   :is-killed is-killed))

(defn- some-context-with-home [parent]
  (some-ctx-with :config {:home-dir parent}))

(defn- execute-wait-for-async
  ([git-src-dir last-seen-revision]
   (execute-wait-for-async git-src-dir last-seen-revision (async/chan 100) (atom false)))
  ([git-src-dir last-seen-revision result-channel is-killed]
    (let [ctx (some-context-with last-seen-revision result-channel is-killed)
          ch (async/go (wait-for-git ctx (repo-uri-for git-src-dir) "master" :ms-between-polls 100))]
      (Thread/sleep 500) ;; dirty hack to make sure we started waiting before making the next commit
      ch)))

(deftest wait-for-git-test
  (testing "that it waits for a new commit to happen"
    (let [test-repo (create-test-repo)
          git-src-dir (:dir test-repo)
          original-head-commit (last (:commits test-repo))
          wait-started-while-not-having-a-new-commit-ch (execute-wait-for-async git-src-dir original-head-commit)
          commit-hash-after-waiting-started-already (commit-to git-src-dir)
          wait-started-while-not-having-a-new-commit-result (get-value-or-timeout-from wait-started-while-not-having-a-new-commit-ch)]
      (is (= commit-hash-after-waiting-started-already (:revision wait-started-while-not-having-a-new-commit-result)))
      (is (= original-head-commit (:old-revision wait-started-while-not-having-a-new-commit-result)))
      (is (= :success (:status wait-started-while-not-having-a-new-commit-result)))))
  (testing "that waiting returns immediately when a commit happened while it was not waiting"
    (let [test-repo (create-test-repo)
          git-src-dir (:dir test-repo)
          original-head-commit (last (:commits test-repo))
          commit-hash-with-nothing-waiting-for-it (commit-to git-src-dir)
          wait-for-commit-that-happend-while-not-waiting-ch-result  (get-value-or-timeout-from (execute-wait-for-async git-src-dir original-head-commit))]
      (is (= commit-hash-with-nothing-waiting-for-it (:revision wait-for-commit-that-happend-while-not-waiting-ch-result)))
      (is (= original-head-commit (:old-revision wait-for-commit-that-happend-while-not-waiting-ch-result)))
      (is (= :success (:status wait-for-commit-that-happend-while-not-waiting-ch-result)))))
  (testing "that when no previous commit is known, we just wait for the next one"
    (let [git-src-dir (:dir (create-test-repo))
          wait-for-ch (execute-wait-for-async git-src-dir nil)
          new-commit-hash (commit-to git-src-dir)
          wait-for-result (get-value-or-timeout-from wait-for-ch)]
      (is (= new-commit-hash (:revision wait-for-result)))
      (is (= :success (:status wait-for-result)))))
  (testing "that when no previous commit is known the old revision is the HEAD when we started waiting"
    (let [test-repo (create-test-repo)
          git-src-dir (:dir test-repo)
          original-head-commit (last (:commits test-repo))
          wait-for-ch (execute-wait-for-async git-src-dir nil)
          _ (commit-to git-src-dir)
          wait-for-result (get-value-or-timeout-from wait-for-ch)]
      (is (= original-head-commit (:old-revision wait-for-result)))))
  (testing "that wait-for can be killed and that the last seen revision is being kept"
    (let [is-killed (atom false)
          test-repo (create-test-repo)
          git-src-dir (:dir test-repo)
          original-head-commit (last (:commits test-repo))
          wait-for-ch (execute-wait-for-async git-src-dir nil (async/chan 10) is-killed)
          _ (reset! is-killed true)
          wait-for-result (get-value-or-timeout-from wait-for-ch)]
      (is (= :killed (:status wait-for-result)))
      (is (= original-head-commit (:_git-last-seen-revision wait-for-result)))))
  (testing "that it retries until being killed if the repository cannot be reached"
    (let [is-killed (atom false)
          result-ch (execute-wait-for-async "some-uri-that-doesnt-exist" nil (async/chan 10) is-killed)]
      (reset! is-killed true)
      (is (= :killed (:status (async/<!! result-ch)))))))

(deftest with-git-test
  (testing "that it checks out a particular revision and then returns, indicating the location of the checked out repo as :cwd value and that the cloned repo is in a directory named like the repo that was cloned"
    (let [create-output (create-test-repo)
          git-src-dir (:dir create-output)
          commits (:commits create-output)
          first-commit (first commits)
          with-git-args { :revision first-commit }
          with-git-function (with-git (repo-uri-for git-src-dir) [step-that-returns-the-current-cwd-head])
          with-git-result (with-git-function with-git-args (some-ctx))]
      (is (= first-commit (:current-head (get (:outputs with-git-result ) [1 42]))))
      (is (.startsWith (:out with-git-result) "Cloning"))))
  (testing "that it fails when it couldn't check out a repository"
    (let [with-git-args { :revision "some-commit" }
          with-git-function (with-git "some-unknown-uri" [])
          with-git-result (with-git-function with-git-args (some-ctx))]
      (is (=  :failure (:status with-git-result)))
      (is (.contains (:out with-git-result) "some-unknown-uri" )))))

(deftest with-git-branch-test
  (testing "that it checks out the head revision for a given branch and then returns, indicating the location of the checked out repo as :cwd value and that the cloned repo is in a directory named like the repo that was cloned"
    (let [create-output (create-test-repo-with-branch)
          git-src-dir (:dir create-output)
          commits (:commits create-output)
          lastest-commit (last commits)
          with-git-function (with-git-branch (repo-uri-for git-src-dir) "develop" [step-that-returns-the-current-cwd-head])
          with-git-result (with-git-function {} (some-ctx))]
      (is (= lastest-commit (:current-head (get (:outputs with-git-result ) [1 42]))))
      (is (.startsWith (:out with-git-result) "Cloning"))))
  (testing "that it fails when it couldn't check out a repository"
    (let [with-git-function (with-git-branch "some-unknown-uri" "some-unknown-branch" [])
          with-git-result (with-git-function {} (some-ctx))]
      (is (=  :failure (:status with-git-result)))
      (is (.contains (:out with-git-result) "some-unknown-uri" ))))
  (testing "that it fails when it couldn't check out a branch"
    (let [create-output (create-test-repo-with-branch)
          git-src-dir (:dir create-output)
          with-git-function (with-git-branch (repo-uri-for git-src-dir) "some-unknown-branch" [])
          with-git-result (with-git-function {} (some-ctx))]
      (is (=  :failure (:status with-git-result)))
      (is (.contains (:out with-git-result) "some-unknown-branch" )))))

(defn some-step-that-returns-42 [args ctx]
  {:status :success :the-number 42})
(defn some-step-that-returns-21 [args ctx]
  {:status :success :the-number 21})
(defn some-step-that-returns-the-cwd [{cwd :cwd} _]
  {:status :success :thecwd cwd})
(defn some-step-that-returns-a-global-value [& _]
  {:status :success :global {:some :value}})

(defn some-step-waiting-to-be-killed [_ ctx]
  (loop [counter 0]
    (killable/if-not-killed ctx
      (if (< counter 100) ;; make sure the step always eventually finishes
        (do
          (Thread/sleep 100)
          (recur (inc counter)))
        {:status :waited-too-long}))))

(deftest checkout-and-execute-test
  (let [some-parent-folder (temp-util/create-temp-dir)
        create-output (create-test-repo)
        git-src-dir (:dir create-output)
        repo-uri (repo-uri-for git-src-dir)
        ctx (some-context-with-home some-parent-folder)
        args {}]
    (testing "that it returns the results of the last step it executed"
      (is (map-containing {:the-number 42 } (checkout-and-execute repo-uri "HEAD" args (some-ctx) [some-step-that-returns-21 some-step-that-returns-42]))))
    (testing "that global values can be returned from any step and will be part of the final result"
      (is (map-containing {:global {:some :value}} (checkout-and-execute repo-uri "HEAD" args (some-ctx) [some-step-that-returns-a-global-value some-step-that-returns-42]))))
    (testing "that the git repo is checked out somewhere within the home folder"
      (is (= some-parent-folder (.getParent (.getParentFile (io/file (:thecwd (checkout-and-execute repo-uri "HEAD" args ctx [some-step-that-returns-the-cwd]))))))))
    (testing "that the children can be killed"
      (without-dead-steps
        (let [is-killed     (atom false)
              ctx           (some-ctx-with :config {:home-dir some-parent-folder}
                                           :step-id [0]
                                           :is-killed is-killed)
              future-result (start-waiting-for (checkout-and-execute repo-uri "HEAD" args ctx [some-step-waiting-to-be-killed]))]
          (wait-for (tu/child-step-running? ctx [1 0]))
          (reset! is-killed true)
          (is (map-containing {:status  :killed
                               :outputs {[1 0] {:status        :killed
                                                :received-kill true}}} (get-or-timeout future-result))))))))


(deftest with-commit-details-test
  (testing "that we can get the details between two commits and whatever we put in"
    (let [test-repo    (create-test-repo)
          old-revision (last (:commits test-repo))
          git-dir      (:dir test-repo)
          commit       (commit-to git-dir "some commit")
          other-commit (commit-to git-dir "some other commit")
          args         {:status :success :foo :bar :revision other-commit :old-revision old-revision}
          result       (with-commit-details (some-ctx) (repo-uri-for git-dir) args)]
      (is (= :success (:status result)))
      (is (= :bar (:foo result)))
      (is (= other-commit (:revision result)))
      (is (= old-revision (:old-revision result)))
      (is (= {commit       {:msg "some commit"}
              other-commit {:msg "some other commit"}} (:commits result)))
      (is (.contains (:out result) "some commit"))
      (is (.contains (:out result) "some other "))
      (is (.contains (:out result) commit))
      (is (.contains (:out result) other-commit)))))

(deftest wait-with-details-test
  (testing "that we can kill the wait"
    (let [test-repo (create-test-repo)
          git-dir (:dir test-repo)
          ctx (assoc (some-ctx) :is-killed (atom true))
          result (wait-with-details ctx (repo-uri-for git-dir) "master" :ms-between-polls 100)]
      (is (= :killed (:status result))))))
