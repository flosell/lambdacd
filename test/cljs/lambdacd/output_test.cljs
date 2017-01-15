(ns lambdacd.output-test
  (:require [cljs.test :refer-macros [deftest is testing run-tests]]
            [lambdacd.dom-utils :as dom]
            [lambdacd.db :as db]
            [dommy.core :refer-macros [sel sel1]]
            [lambdacd.testutils :as tu]
            [re-frame.core :as re-frame]
            [lambdacd.output :as output]
            [lambdacd.testutils :refer [mock-subscriptions]]
            [clojure.walk :as walk]
            [lambdacd.utils :as utils]))

(def some-step-id [4 2])

(deftest output-test
  (testing "that a help message is shown when no step selected"
    (with-redefs [re-frame/subscribe (mock-subscriptions {::db/current-step-result {:result {:status "running" :out "hello from child"}}
                                                          ::db/step-id nil})]
      (tu/with-mounted-component
        (output/output-component)
        (fn [c div]
          (is (dom/found-in div #"to display details"))))))
  (testing "that we display the step output when a step-id is presented"
    (with-redefs [re-frame/subscribe (mock-subscriptions {::db/current-step-result      {:result {:status   "running"
                                                                                                  :some-key :some-value
                                                                                                  :out      "hello from child"
                                                                                                  :details  [{:label "some details"}]}}
                                                          ::db/raw-step-results-visible true
                                                          ::db/step-id some-step-id})]
      (tu/with-mounted-component
        (output/output-component)
        (fn [c div]
          (is (dom/found-in div #"hello from child"))
          (is (dom/found-in div #"some details"))
          (is (dom/found-in div #"some-key"))
          (is (dom/found-in div #"some-value"))))))
  (testing "issue #100"
    (with-redefs [re-frame/subscribe (mock-subscriptions {::db/current-step-result      {:result {:status   "running"
                                                                                                  :some-key :some-value
                                                                                                  :all-revisions { (keyword "refs/heads/master") "some-sha" }
                                                                                                  :out      "hello"}}
                                                          ::db/raw-step-results-visible true
                                                          ::db/step-id some-step-id})]
      (tu/with-mounted-component
        (output/output-component)
        (fn [c div]
          (is (dom/found-in div #"hello"))
          (is (dom/found-in div #"refs/heads/master")))))))

(deftest stringify-keys-test
  (testing "that keywords in maps are properly stringified for rendering"
    (is (= { ":foo" 42} (utils/stringify-keys {:foo 42}))))
  (testing "that strange keywords with namespaces are supported (#100)"
    (is (= { ":refs/heads/master" "some-sha"} (utils/stringify-keys { (keyword "refs/heads/master") "some-sha" })))))

(deftest console-component-test
  (testing "that we can display the :out output of a step"
    (with-redefs [re-frame/subscribe (mock-subscriptions {::db/current-step-result {:result {:status "running" :some-key :some-value :out "hello from child"}}})]
    (tu/with-mounted-component
      (output/console-component)
      (fn [c div]
        (is (dom/found-in div #"hello from child"))))))
  (testing "kill messages"
    (testing "that the output contains a message indicating that a kill was received"
      (with-redefs [re-frame/subscribe (mock-subscriptions {::db/current-step-result {:result {:status "running" :out "hello" :received-kill true}}})]
        (tu/with-mounted-component
          (output/console-component)
          (fn [c div]
            (is (dom/found-in div #"LambdaCD received kill"))))))
    (testing "that the output contains a message indicating that a kill was processed"
      (with-redefs [re-frame/subscribe (mock-subscriptions {::db/current-step-result {:result {:status "running" :out "hello" :processed-kill true :received-kill true}}})]
        (tu/with-mounted-component
          (output/console-component)
          (fn [c div]
            (is (dom/found-in div #"Step received kill"))))))
    (testing "that the output contains no message indicating that a kill was processed after the step is dead"
      (with-redefs [re-frame/subscribe (mock-subscriptions {::db/current-step-result {:result {:status "killed" :out "hello" :processed-kill true :received-kill true}}})]
        (tu/with-mounted-component
          (output/console-component)
          (fn [c div]
            (is (dom/not-found-in div #"Step received kill"))))))
    (testing "that the output contains no message indicating that a kill was received after the step is dead"
      (with-redefs [re-frame/subscribe (mock-subscriptions {::db/current-step-result {:result {:status "killed" :out "hello" :received-kill true}}})]
        (tu/with-mounted-component
          (output/console-component)
          (fn [c div]
            (is (dom/not-found-in div #"LambdaCD received kill")))))))
  (testing "finished message"
    (with-redefs [re-frame/subscribe (mock-subscriptions {::db/current-step-result {:result {:status "success" :out ""}}})]
      (testing "that the output contains a message indicating the success of a build step"
        (tu/with-mounted-component
          (output/console-component)
          (fn [c div]
            (is (dom/found-in div #"Step is finished: SUCCESS"))))))
    (testing "that the output contains a message indicating the failure of a build step"
      (with-redefs [re-frame/subscribe (mock-subscriptions {::db/current-step-result {:result {:status "failure" :out ""}}})]
        (tu/with-mounted-component
          (output/console-component)
          (fn [c div]
            (is (dom/found-in div #"Step is finished: FAILURE"))))))
    (testing "that the finished message does not appear if the step is still running"
      (with-redefs [re-frame/subscribe (mock-subscriptions {::db/current-step-result {:result {:status "running" :out "hello from root"}}})]
        (tu/with-mounted-component
          (output/console-component)
          (fn [c div]
            (is (dom/not-found-in div #"Step is finished")))))
      (with-redefs [re-frame/subscribe (mock-subscriptions {::db/current-step-result {:result {:status "waiting" :out "waiting..."}}})]
        (tu/with-mounted-component
          (output/console-component)
          (fn [c div]
            (is (dom/not-found-in div #"Step is finished"))))))
    (testing "that the finished message does not appear if the step doesn't have a known state"
      (with-redefs [re-frame/subscribe (mock-subscriptions {::db/current-step-result {:result {:status nil :out ""}}})]
        (tu/with-mounted-component
          (output/console-component)
          (fn [c div]
            (is (dom/not-found-in div #"Step is finished")))))
      (with-redefs [re-frame/subscribe (mock-subscriptions {::db/current-step-result {:result {:status "waiting" :out ""}}})]
        (tu/with-mounted-component
          (output/console-component)
          (fn [c div]
            (is (dom/not-found-in div #"Step is finished")))))))
  (testing "that the console output does not appear if no :out is there"
    (with-redefs [re-frame/subscribe (mock-subscriptions {::db/current-step-result {:result {:status :success}}})]
      (tu/with-mounted-component
        (output/console-component)
        (fn [c div]
          (is (dom/not-found-in div #"Output")))))))

(deftest raw-step-results-component-test
  (testing "that we can display the other attributes of the output map"
    (with-redefs [re-frame/subscribe (mock-subscriptions {::db/current-step-result      {:some-key :some-value}
                                                          ::db/raw-step-results-visible true})]
      (tu/with-mounted-component
        (output/raw-step-results-component)
        (fn [c div]
          (is (dom/found-in (sel1 div :button) #"hide"))
          (is (dom/found-in div #"some-key"))
          (is (dom/found-in div #"some-value"))))))
  (testing "that we can hide the other attributes of the output map"
    (with-redefs [re-frame/subscribe (mock-subscriptions {::db/current-step-result      {:some-key :some-value}
                                                          ::db/raw-step-results-visible false})]
      (tu/with-mounted-component
        (output/raw-step-results-component)
        (fn [c div]
          (is (dom/found-in (sel1 div :button) #"show"))
          (is (dom/not-found-in div #"some-key"))
          (is (dom/not-found-in div #"some-value")))))))

(deftest details-component-test
  (testing "that the details are displayed if present"
    (with-redefs [re-frame/subscribe (mock-subscriptions {::db/current-step-result {:result {:details [{:label   "some details"
                                                                                                        :href    "http://some-url.com"
                                                                                                        :details [{:label "some nested details"}]}
                                                                                                       {:label "some preformatted text following"
                                                                                                        :raw   "this preformatted text"}]}}})]
      (tu/with-mounted-component
        (output/details-component)
        (fn [c div]
          (is (dom/found-in div #"some details"))
          (is (dom/containing-link-to div "http://some-url.com"))
          (is (dom/found-in div #"nested details"))
          (is (dom/found-in div #"preformatted text following"))
          (is (dom/containing-preformatted-text div #"this preformatted text"))))))
  (testing "that if two details have the same label, both are displayed"
    (with-redefs [re-frame/subscribe (mock-subscriptions {::db/current-step-result {:result {:status  "success"
                                                                                             :details [{:label "foo"
                                                                                                        :href  "http://some-url.com"}
                                                                                                       {:label "foo"
                                                                                                        :href  "http://some-other-url.com"}]}}})]
      (tu/with-mounted-component
        (output/details-component)
        (fn [c div]
          (is (dom/containing-link-to div "http://some-url.com"))
          (is (dom/containing-link-to div "http://some-other-url.com")))))))


(deftest ansi-fragment->classes-test
  (testing "that we correctly convert fragments with ansiparse information to a css class"
    (is (= "" (output/ansi-fragment->classes {:bold false})))
    (is (= "console-output__line--bold" (output/ansi-fragment->classes {:bold true})))
    (is (= "console-output__line--italic" (output/ansi-fragment->classes {:italic true})))
    (is (= "console-output__line--underline" (output/ansi-fragment->classes {:underline true})))
    (is (= "console-output__line--fg-red" (output/ansi-fragment->classes {:foreground "red"})))
    (is (= "console-output__line--fg-black" (output/ansi-fragment->classes {:foreground "black"})))
    (is (= "console-output__line--bg-blue" (output/ansi-fragment->classes {:background "blue"}))))
  (testing "that combinations work"
    (is (= "console-output__line--bold console-output__line--italic console-output__line--bg-blue console-output__line--fg-black"
           (output/ansi-fragment->classes {:background "blue" :foreground "black" :bold true :italic true}))))
  (testing "that unknown things get ignored"
    (is (= "" (output/ansi-fragment->classes {:something-unknown true})))))
