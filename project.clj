(defproject lambdacd "0.1.0-alpha2"
  :description "a library to create a continous delivery pipeline in code"
  :url "http://github.com/flosell/lambdacd"
  :license {:name "Apache License, version 2.0"
            :url "http://www.apache.org/licenses/LICENSE-2.0.html"}
  :dependencies [[org.clojure/clojure "1.5.1"]
                 [org.clojure/data.json "0.2.5"]
                 [me.raynes/conch "0.8.0"]
                 [org.clojure/core.async "0.1.338.0-5c5012-alpha"]
                 [compojure "1.1.8"]
                 [org.clojure/tools.logging "0.3.0"]
                 [org.slf4j/slf4j-api "1.7.5"]
                 [ch.qos.logback/logback-core "1.0.13"]
                 [ch.qos.logback/logback-classic "1.0.13"]]
  :ring {:handler todopipeline.pipeline/app
         :init todopipeline.pipeline/start-pipeline-thread }
  :plugins [[lein-ring "0.8.11"]
            [lein-kibit "0.0.8"]])
