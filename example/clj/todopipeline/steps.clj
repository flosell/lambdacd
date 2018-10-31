;; # Build steps
;; This namespace contains the actual buildsteps that test, package and deploy your application.
;; They are used in the pipeline-definition you saw in todopipeline.pipeline

(ns todopipeline.steps
  (:require [lambdacd.steps.shell :as shell]
            [lambdacd.steps.manualtrigger :as manualtrigger]
            [lambdacd-git.core :as git]
            [clj-time.format :as time-format]
            [clj-time.core :as time]
            [lambdacd.steps.control-flow :refer [with-workspace]]
            [lambdacd.stepsupport.metadata :as metadata]
            [lambdacd.stepsupport.output :as output]
            [lambdacd.stepsupport.chaining :as chaining]))

;; Let's define some constants and utility-functions
(def backend-repo "git@github.com:flosell/todo-backend-compojure")
(def frontend-repo "git@github.com:flosell/todo-backend-client.git")

(defn build-label [ctx]
  (str (time-format/unparse (time-format/formatters :date) (time/now)) "." (:build-number ctx)))

(defn- pass-on-previous [args]
  ; previous steps need what came back from the initial trigger so we just pass this stuff on to the next step...
  ; Could also use :global instead
  args)

; Let's try out some custom metadata:
(defn set-build-name [args ctx]
  (metadata/assoc-build-metadata! ctx :human-readable-build-label (build-label ctx))
  (pass-on-previous args))

;; This step does nothing more than to delegate to a library-function.
;; It's a function that just waits until something changes in the repo.
;; Once done, it returns and the build can go on
(defn wait-for-frontend-repo [_ ctx]
  (let [wait-result (git/wait-for-git ctx frontend-repo)
        frontend-revision (:revision wait-result)]
    (assoc wait-result :frontend-revision frontend-revision
                       :backend-revision "HEAD")))

(defn wait-for-backend-repo [_ ctx]
  (let [wait-result (git/wait-for-git ctx backend-repo)
        backend-revision (:revision wait-result)]
    (assoc wait-result :frontend-revision "HEAD"
                       :backend-revision backend-revision)))


;; Checkout the revision passed down in args from of each repository
;; into current working directory
(defn clone-frontend
  [args ctx]
  (git/clone ctx frontend-repo (:frontend-revision args) (:cwd args)))

(defn clone-backend
  [args ctx]
  (git/clone ctx backend-repo (:backend-revision args) (:cwd args)))


(defn wait-for-greeting [args ctx]
  (manualtrigger/parameterized-trigger {:greeting { :desc "some greeting"}} ctx))

(defn demonstrate-ansi [args ctx]
  (shell/bash ctx "/"
              "printf \"\\033[0mAll attributes off\\033[0m\\n\""
              "printf \"\\033[1mBold\\033[0m\\n\""
              "printf \"\\033[4mUnderline\\033[0m\\n\""
              "printf \"\\033[5mBlink\\033[0m\\n\""
              "printf \"\\033[8mHide\\033[0m\\n\""
              "printf \"\\033[30mBlack\\033[0m\\n\""
              "printf \"\\033[31mRed\\033[0m\\n\""
              "printf \"\\033[32mGreen\\033[0m\\n\""
              "printf \"\\033[33mYellow\\033[0m\\n\""
              "printf \"\\033[34mBlue\\033[0m\\n\""
              "printf \"\\033[35mMagenta\\033[0m\\n\""
              "printf \"\\033[36mCyan\\033[0m\\n\""
              "printf \"\\033[37mWhite\\033[0m\\n\""
              "printf \"\\033[40m\\033[37mBlack Background\\033[0m\\n\""
              "printf \"\\033[41mRead Background\\033[0m\\n\""
              "printf \"\\033[42mGreen Background\\033[0m\\n\""
              "printf \"\\033[43mYellow Background\\033[0m\\n\""
              "printf \"\\033[44mBlue Background\\033[0m\\n\""
              "printf \"\\033[45mMagenta Background\\033[0m\\n\""
              "printf \"\\033[46mCyan Background\\033[0m\\n\""
              "printf \"\\033[47mWhite Background\\033[0m\\n\""
              "printf \"\\033[1mBold\nwith newlines\\033[0m\\n\""
              "printf \"Normal, \\033[1mbold\\033[0m and then normal again\\n\""))

(defn create-some-details [args ctx]
  {:status  :success
   :details [{:label   "Some Links"
              :details [{:label "Builds API"
                         :href  "/api/builds/"}
                        {:label "Github"
                         :href  "https://github.com/flosell/lambdacd"}]}
             {:label   "Mock test results"
              :details [{:label "Unit Tests: 0/10 failed"}
                        {:label   "Integration Tests Tests: 1/5 failed"
                         :details [{:label "SomeTestClass.shouldBeFailingWhenTested"
                                    :raw   "java.lang.AssertionError: expected:<0> but was:<10>\n\tat org.junit.Assert.fail(Assert.java:88)\n\tat org.junit.Assert.failNotEquals(Assert.java:743)\n\tat org.junit.Assert.assertEquals(Assert.java:118)"}]}]}]})

;; The steps that do the real work testing, packaging, publishing our code.
;; They get the :cwd argument from the ```with-*-git steps``` we defined above.
(defn client-package [{cwd :cwd greeting :greeting} ctx]
  (output/capture-output ctx
                          (println "This is an optional greeting: " greeting)
                          (shell/bash ctx cwd
                                      "bower install"
                                      "./package.sh")))

;; this step depends on the fact that we packaged the artifact first and in the same workspace.
;; to make sure retriggering doesnt mess with this (as it sets up a new workspace), we declare this step as dependent
(defn ^{:depends-on-previous-steps true} client-publish [{cwd :cwd} ctx]
  (shell/bash ctx cwd
              "./publish.sh"))

(defn server-test [{cwd :cwd} ctx]
  (println "server test cwd: " cwd)
  (shell/bash ctx cwd
    "lein test"))

(defn server-package [{cwd :cwd} ctx]
  (println "server package cwd: " cwd)
  (shell/bash ctx cwd
    "lein uberjar"))

(defn ^{:depends-on-previous-steps true} server-publish [{cwd :cwd} ctx]
  (shell/bash ctx cwd
              "./publish.sh"))

(defn server-deploy-ci [{cwd :cwd} ctx]
  (shell/bash ctx cwd "./deploy-server.sh backend_ci /tmp/mockrepo/server-snapshot.tar.gz"))

(defn client-deploy-ci [{cwd :cwd} ctx]
  (shell/bash ctx cwd "./deploy-frontend.sh frontend_ci /tmp/mockrepo/client-snapshot.tar.gz"))

;; This is just a step that shows you what output steps actually have (since you have only used library functions up to
;; here). It's just a map with some information. :status has a special meaning in the sense that it needs to be there
;; and be :success for the step to be treated as successful and for the build to continue.
;; all other data can be (more or less) arbitrary and will be passed on to the next build-step as input arguments.
;; more or less means that some values have special meaning for the UI to display things.
;; Check out the implementation of ```shell/bash``` if you want to know more.
(defn some-step-that-cant-be-reached [& _]
  { :some-info "hello world"
    :status :success})


;; Another step that just fails using bash.
;; We could have made a failing step easier as well by just returning ```{ :status :failure }```
(defn some-failing-step [_ ctx]
  (shell/bash ctx "/" "echo \"i am going to fail now...\"" "exit 1"))


(defn always-echo-something [_ ctx]
  (shell/bash ctx "/" "while true; do date; sleep 1; done"))

(defn check-greeting [{greeting :greeting} ctx]
  (if (= "fail" greeting)
    (shell/bash ctx "/"
                "echo comparison failed"
                "exit 1")
    (shell/bash ctx "/"
                "echo comparison success"
                "echo to make this step fail, input fail into the wait-for-greeting step"
                "exit 0")))

(defn do-succeed [args ctx]
  (println "args: " args)
  {:status :success})

(defn do-stuff [args ctx]
  (chaining/chaining args ctx
                    (shell/bash chaining/injected-ctx "/" "echo one && sleep 10 && echo  two")
                    (shell/bash chaining/injected-ctx "/" "echo three && sleep 10 && echo four")))

(defn compare-screenshots [args ctx]
  (chaining/last-step-status-wins
    (chaining/always-chaining args ctx
      (check-greeting chaining/injected-args chaining/injected-ctx)
      (if (= :failure (:status chaining/injected-args))
        (manualtrigger/wait-for-manual-trigger chaining/injected-args chaining/injected-ctx)
        {:status :success})))) ; else-branch seems to be necessary at the moment, otherwise nil will be returned
