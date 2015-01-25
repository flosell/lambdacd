(ns smoketest.smoke-test
  (:require [smoketest.steps :as steps]
            [ring.server.standalone :as ring :only serve]
            [org.httpkit.client :as http]
            [clojure.test :refer :all]
            [clojure.data.json :as json]
            [lambdacd.util :as util]))


(def url-base "http://localhost:3000/old")
(defn- test-server [handler options]
  (ring/serve handler (merge {:join? false, :open-browser? false} options)))

(defn- server-status []
  (:status (deref (http/get (str url-base "/api/pipeline-state")))))

(defn- pipeline-state []
  (json/read-str (:body (deref (http/get (str url-base "/api/pipeline-state"))))))

(defn first-build []
  (get (pipeline-state) "1"))

(defn- manual-trigger []
  (get (first-build) "(1)"))

(defn- manual-trigger-state []
  (get (manual-trigger)  "status"))

(defn- manual-trigger-id []
  (get (manual-trigger) "trigger-id"))

(defn- trigger-manual-trigger []
  (:status (deref (http/post (str (str url-base "/api/dynamic/") (manual-trigger-id)) {:body "{}" :headers { "Content-Type" "application/json"}}))))

(defn wait-a-bit []
  (Thread/sleep 2000)) ; TODO: make more robust, wait for something specific

(defmacro with-server [server & body]
  `(let [server# ~server]
     (try
       ~@body
       (finally (.stop server#)))))

(defn- create-test-repo-at [dir]
  (util/bash dir
             "git init"
             "echo \"world\" > foo"
             "git add -A"
             "git commit -m \"some message\""))


(deftest ^:smoke smoke-test
  (testing "that we can run a pipeline"
    (create-test-repo-at steps/some-repo-location)
    (with-server (test-server smoketest.pipeline/app { :init smoketest.pipeline/start-pipeline-thread })
      (is (= 200 (server-status)))
      (is (= "waiting" (manual-trigger-state)))
      (is (= 200 (trigger-manual-trigger)))
      (wait-a-bit)
      (is (= "success" (manual-trigger-state)))
      (is (= 5 @steps/some-counter))
      (is (= "world\n" @steps/some-value-read-from-git-repo))
      )))