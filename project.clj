(defproject lambdaci "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.5.1"]
                 [org.clojure/tools.nrepl "0.2.3"]
                 [org.clojure/data.json "0.2.5"]
                 [me.raynes/conch "0.8.0"]
                 [org.clojure/core.async "0.1.338.0-5c5012-alpha"]
                 [compojure "1.1.8"]]
  :ring {:handler lambdaci.server/app
         :init lambdaci.server/start-pipeline-thread }
  :plugins [[lein-ring "0.8.11"]]
  :main lambdaci.core)
