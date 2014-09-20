(ns smoketest.smoke-test
  (:require [smoketest.pipeline :as pipeline]
            [smoketest.steps :as steps]
            [ring.server.standalone :as ring :only serve]
            [org.httpkit.client :as http]
            [clojure.test :refer :all]
            [clojure.data.json :as json]))


(defn- test-server [handler options]
  (ring/serve handler (merge {:join? false, :open-browser? false} options)))

"{\"(1)\":{\"trigger-id\":\"8b2dd783-2335-477d-b6ec-9e4481ff7d0e\",\"status\":\"waiting\"}}"
(defn- server-status []
  (:status (deref (http/get "http://localhost:3000/api/pipeline-state"))))

(defn- pipeline-state []
  (json/read-str (:body (deref (http/get "http://localhost:3000/api/pipeline-state")))))

(defn- manual-trigger []
  (get (pipeline-state) "(1)"))

(defn- manual-trigger-state []
  (get (manual-trigger)  "status"))

(defn- manual-trigger-id []
  (get (manual-trigger) "trigger-id"))

(defn- trigger-manual-trigger []
  (:status (deref (http/post (str "http://localhost:3000/api/dynamic/" (manual-trigger-id))))))

(defn wait-a-bit []
  (Thread/sleep 2000)) ; TODO: make more robust, wait for something specific

(defmacro with-server [server & body]
  `(let [server# ~server]
     (try
       ~@body
       (finally (.stop server#)))))


(deftest ^:smoke smoke-test
  (testing "that we can run a pipeline"
    (with-server (test-server smoketest.pipeline/app { :init smoketest.pipeline/start-pipeline-thread })
      (is (= 200 (server-status)))
      (is (= "waiting" (manual-trigger-state)))
      (is (= 200 (trigger-manual-trigger)))
      (wait-a-bit)
      (is (= "waiting" (manual-trigger-state)))
      (is (= 5 @steps/some-counter))
      )))