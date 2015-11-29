(ns lambdacd.db-test
  (:require
    [cljs.test :refer-macros [deftest is testing run-tests]]
    [lambdacd.testdata :refer [some-build-step with-name with-type with-output with-children with-step-id with-status]]
    [reagent.core :as r]
    [lambdacd.db :as db]))

(def some-other-step
  (-> some-build-step
      (with-step-id [8 9])
      (with-name "some-other-step")))

(def some-container-build-step
  (-> some-build-step
      (with-name "some-container")
      (with-type "container")
      (with-children [some-build-step])
      (with-output "hello from container")))

(def some-parallel-build-step
  (-> some-container-build-step
      (with-name "some-parallel-step")
      (with-type "parallel")
      (with-children [some-other-step])
      (with-output "hello from p")))

(deftest get-set-history-test
         (testing "that we can set and update history"
                  (let [db (r/atom db/default-db)]
                    (reset! db (db/history-updated-handler @db [nil [:some :history]]))
                    (is (= [:some :history] @(db/history-subscription db nil)))))
         (testing "that setting the history does set the current build number to the most recent build if none is set"
                  (let [db (r/atom db/default-db)]
                    (reset! db (db/history-updated-handler @db [nil [{:build-number 1} {:build-number 3} {:build-number 2}]]))
                    (is (= 3 @(db/build-number-subscription db nil)))))
         (testing "that setting the history does not set the current build number if a different build number is already set"
                  (let [db (r/atom (assoc db/default-db :displayed-build-number 10))]
                    (reset! db (db/history-updated-handler @db [nil [{:build-number 1} {:build-number 3} {:build-number 2}]]))
                    (is (= 10 @(db/build-number-subscription db nil))))))

(deftest get-set-pipeline-state-test
  (testing "that we can set and update history"
    (let [db (r/atom db/default-db)]
      (reset! db (db/pipeline-state-updated-handler @db [nil [:some :state]]))
      (is (= [:some :state] @(db/pipeline-state-subscription db nil))))))

(deftest lost-connection-test
         (testing "that initially there is no connection"
                  (let [db (r/atom db/default-db)]
                    (is (= :lost @(db/connection-state-subscription db nil)))))
         (testing "that updated state restores the connection state"
                  (let [db (r/atom db/default-db)]
                    (reset! db (db/pipeline-state-updated-handler @db [nil [:some :state]]))
                    (is (= :active @(db/connection-state-subscription db nil)))))
         (testing "that updated history restores the connection state"
                  (let [db (r/atom db/default-db)]
                    (reset! db (db/history-updated-handler @db [nil [:some :state]]))
                    (is (= :active @(db/connection-state-subscription db nil)))))
         (testing "that we can notify about lost connection"
                  (let [db (r/atom db/default-db)]
                    ; first, set it active
                    (reset! db (db/history-updated-handler @db [nil [:some :state]]))
                    (is (= :active @(db/connection-state-subscription db nil)))
                    ; then loose connection
                    (reset! db (db/lost-connection-handler @db [nil]))
                    (is (= :lost @(db/connection-state-subscription db nil))))))

(deftest build-number-test
         (testing "that initially the build number is not set"
                  (let [db (r/atom db/default-db)]
                    (is (nil? @(db/build-number-subscription db nil)))))
         (testing "that the build-number can be updated"
                  (let [db (r/atom db/default-db)]
                    (reset! db (db/build-number-update-handler @db [nil 42]))
                    (is (= 42 @(db/build-number-subscription db nil))))))

(deftest step-id-test
         (testing "that initially the step-id is not set"
                  (let [db (r/atom db/default-db)]
                    (is (nil? @(db/step-id-subscription db nil)))))
         (testing "that the step-id can be updated"
                  (let [db (r/atom db/default-db)]
                    (reset! db (db/step-id-update-handler @db [nil [2 1]]))
                    (is (= [2 1] @(db/step-id-subscription db nil))))))

(deftest raw-step-results-visible-test
         (testing "that initially the raw-step-results-visible information is not set"
                  (let [db (r/atom db/default-db)]
                    (is (= false @(db/raw-step-result-visible-subscription db nil)))))
         (testing "that the visibility can be updated"
                  (let [db (r/atom db/default-db)]
                    (reset! db (db/toggle-raw-step-results-visible-handler @db nil))
                    (is (= true @(db/raw-step-result-visible-subscription db nil)))
                    (reset! db (db/toggle-raw-step-results-visible-handler @db nil))
                    (is (= false @(db/raw-step-result-visible-subscription db nil))))))


(deftest current-step-result-test
  (testing "return the step-result of the current step"
    (let [db (r/atom db/default-db)]
      (reset! db (db/pipeline-state-updated-handler @db [nil [some-parallel-build-step]]))
      (reset! db (db/step-id-update-handler @db [nil [8 9]]))
      (is (= some-other-step @(db/current-step-result-subscription db nil)))))
  (testing "return nothing if no step id set"
    (let [db (r/atom db/default-db)]
      (reset! db (db/pipeline-state-updated-handler @db [nil [some-parallel-build-step]]))
      (is (= nil @(db/current-step-result-subscription db nil))))))

(defn dispatch!
  ([db handler]
   (dispatch! db handler nil))
  ([db handler param]
    (reset! db (handler @db [nil param]))))

(defn query
  ([db subs]
   (query db subs nil))
  ([db subs param]
    @(subs db [nil param])))

(def some-state
  [(-> some-build-step
       (with-step-id [1]))
   (-> some-build-step
       (with-step-id [2]))])

(def some-state-with-children
  [(-> some-build-step
       (with-step-id [1])
       (with-children [(-> some-build-step
                           (with-step-id [1 1]))]))
   (-> some-build-step
       (with-step-id [2]))])

(def some-active-container
  (-> some-build-step
      (with-step-id [1])
      (with-status "running")
      (with-children [(-> some-build-step
                          (with-step-id [1 1])
                          (with-status "running"))])))
(def some-inactive-container
  (-> some-build-step
      (with-step-id [2])
      (with-status "success")
      (with-children [(-> some-build-step
                          (with-step-id [1 2])
                          (with-status "failure"))])))

(def some-failed-container
  (-> some-build-step
      (with-step-id [2])
      (with-status "failure")
      (with-children [(-> some-build-step
                          (with-step-id [1 2])
                          (with-status "failure"))])))

(def some-hierarchical-state-with-active-and-inactive
  [some-active-container
   some-inactive-container])

(deftest all-step-ids-test
  (testing "that we get all step ids in the sequence"
    (is (= #{[1] [2]} (db/all-step-ids {:pipeline-state some-state}))))
  (testing "that we get all step ids in a hierarchy"
    (is (= #{[1] [1 1] [2]} (db/all-step-ids {:pipeline-state some-state-with-children})))))

(deftest step-expansion-test
  (testing "that steps are collapsed per default"
    (let [db (r/atom db/default-db)]
      (dispatch! db db/pipeline-state-updated-handler some-state)
      (is (= false (query db db/step-expanded-subscription [1])))
      (is (= false (query db db/step-expanded-subscription [2])))
      (is (= false (query db db/all-expanded-subscription [2])))
      (is (= true (query db db/all-collapsed-subscription)))))
  (testing "that toggling switches expansion on and off"
    (let [db (r/atom db/default-db)]
      (dispatch! db db/pipeline-state-updated-handler some-state)
      (dispatch! db db/toggle-step-expanded [1])
      (is (= true (query db db/step-expanded-subscription [1])))
      (dispatch! db db/toggle-step-expanded [1])
      (is (= false (query db db/step-expanded-subscription [1])))))
  (testing "that expand-all leads to all steps being expanded"
    (let [db (r/atom db/default-db)]
      (dispatch! db db/pipeline-state-updated-handler some-state)
      (is (= false (query db db/all-expanded-subscription)))
      (dispatch! db db/set-all-expanded-handler)
      (is (= true (query db db/all-expanded-subscription)))
      (is (= true (query db db/step-expanded-subscription [1])))
      (is (= true (query db db/step-expanded-subscription [2])))))
  (testing "that we can collapse individual steps after expanding all"
    (let [db (r/atom db/default-db)]
      (dispatch! db db/pipeline-state-updated-handler some-state)
      (is (= false (query db db/all-expanded-subscription)))
      (dispatch! db db/set-all-expanded-handler)
      (dispatch! db db/toggle-step-expanded [1])
      (is (= false (query db db/all-expanded-subscription)))
      (is (= false (query db db/step-expanded-subscription [1])))
      (is (= true (query db db/step-expanded-subscription [2])))))
  (testing "that we can collapse all steps"
    (let [db (r/atom db/default-db)]
      (dispatch! db db/pipeline-state-updated-handler some-state)
      (dispatch! db db/toggle-step-expanded [1])
      (dispatch! db db/set-all-collapsed-handler)
      (is (= true (query db db/all-collapsed-subscription)))
      (is (= false (query db db/all-expanded-subscription)))
      (is (= false (query db db/step-expanded-subscription [1])))
      (is (= false (query db db/step-expanded-subscription [2])))))
  (testing "expand active"
    (testing "that active steps are expanded and inactive ones arent"
      (let [db (r/atom db/default-db)]
        (dispatch! db db/pipeline-state-updated-handler some-hierarchical-state-with-active-and-inactive)
        (dispatch! db db/toggle-expand-active-handler)
        (is (= true (query db db/step-expanded-subscription [1])))
        (is (= true (query db db/step-expanded-subscription [1 1])))
        (is (= false (query db db/step-expanded-subscription [2])))
        (is (= false (query db db/step-expanded-subscription [2 1])))))
    (testing "that everything behaves as normal if toggled off"
      (let [db (r/atom db/default-db)]
        (dispatch! db db/pipeline-state-updated-handler some-hierarchical-state-with-active-and-inactive)
        (dispatch! db db/toggle-expand-active-handler)
        (dispatch! db db/toggle-expand-active-handler)
        (is (= false (query db db/step-expanded-subscription [1])))
        (is (= false (query db db/step-expanded-subscription [1 1])))
        (is (= false (query db db/step-expanded-subscription [2])))
        (is (= false (query db db/step-expanded-subscription [1 2]))))))
  (testing "expand failed"
    (testing "that failed steps are expanded"
      (let [db (r/atom db/default-db)]
        (dispatch! db db/pipeline-state-updated-handler [some-active-container some-failed-container])
        (dispatch! db db/toggle-expand-failure-handler)
        (is (= false (query db db/step-expanded-subscription [1])))
        (is (= false (query db db/step-expanded-subscription [1 1])))
        (is (= true (query db db/step-expanded-subscription [2])))
        (is (= true (query db db/step-expanded-subscription [1 2])))))))