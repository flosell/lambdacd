(defproject lambdacd "0.1.0-alpha13"
  :description "a library to create a continous delivery pipeline in code"
  :url "http://github.com/flosell/lambdacd"
  :license {:name "Apache License, version 2.0"
            :url "http://www.apache.org/licenses/LICENSE-2.0.html"}
  :min-lein-version "2.5.0"
  :deploy-repositories [["clojars" {:creds :gpg}]]
  :dependencies [[org.clojure/clojure "1.5.1"]
                 [org.clojure/data.json "0.2.5"]
                 [me.raynes/conch "0.8.0"]
                 [org.clojure/core.async "0.1.338.0-5c5012-alpha"]
                 [compojure "1.1.8"]
                 [org.clojure/tools.logging "0.3.0"]
                 [org.slf4j/slf4j-api "1.7.5"]
                 [ch.qos.logback/logback-core "1.0.13"]
                 [ch.qos.logback/logback-classic "1.0.13"]
                 [ring-server "0.3.1"]
                 [ring/ring-json "0.3.1"]
                 [hiccup "1.0.5"]
                 ; test-only
                 [http-kit "2.1.16"]
                 ]
  :test-selectors {:default (complement :smoke)
                   :smoke :smoke
                   :all (constantly true)}
  :ring {:handler todopipeline.pipeline/app
         :init todopipeline.pipeline/start-pipeline-thread }
  :main todopipeline.pipeline
  :profiles {:uberjar {:aot [todopipeline.pipeline.main]}}
  :plugins [[lein-ring "0.8.13"]
            [lein-kibit "0.0.8"]
            [lein-marginalia "0.8.0"]
            [quickie "0.3.6"]])
