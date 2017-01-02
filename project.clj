(defproject lambdacd "0.13.0-SNAPSHOT"
  :description "a library to create a continous delivery pipeline in code"
  :url "http://github.com/flosell/lambdacd"
  :license {:name "Apache License, version 2.0"
            :url "http://www.apache.org/licenses/LICENSE-2.0.html"}
  :min-lein-version "2.5.0"
  :deploy-repositories [["clojars" {:creds :gpg}]
                        ["releases" :clojars]]
  :repositories [["activeon" "http://repository.activeeon.com/content/repositories/releases/"]] ; for process tree killer
  :source-paths ["src/clj" "src/cljs"]
  :test-paths ["test/clj" "example/clj"]
  :jar-exclusions [#"logback.xml"]
  :dependencies [[org.clojure/clojure "1.7.0"]
                 [throttler "1.0.0"]
                 [hiccup "1.0.5"]
                 [org.clojure/data.json "0.2.6"]
                 [me.raynes/conch "0.8.0"]
                 [me.raynes/fs "1.4.6"]
                 [org.clojure/core.async "0.1.338.0-5c5012-alpha"]
                 [compojure "1.1.8"]
                 [org.clojure/tools.logging "0.3.1"]
                 [org.slf4j/slf4j-api "1.7.21"]
                 [ring/ring-json "0.3.1"]
                 [cheshire "5.4.0"]
                 [cljsjs/moment "2.10.6-4"]
                 [clj-time "0.9.0"]
                 [org.ow2.proactive/process-tree-killer "1.0.0"]
                 [clj-timeframes "0.1.0"]]
  :exclusions [org.jvnet.winp/winp] ; process-tree-killer depends on this for windows only and doesnt provide it...
  :test-selectors {:default (complement :smoke)
                   :smoke :smoke
                   :all (constantly true)}
  :plugins [
            [lein-cljsbuild "1.1.3"]
            [lein-doo "0.1.4"]
            [lein-environ "1.0.2"]
            [lein-kibit "0.1.2"]
            [quickie "0.3.6"]]
  :clean-targets ^{:protect false} [:target-path :compile-path "resources/public/js-gen"]

  :cljsbuild {:builds {:app {:source-paths ["src/cljs" "env/prod/cljs"]
                             :compiler {:output-to     "resources/public/js-gen/app.js"
                                        :output-dir    "resources/public/js-gen/out"
                                        :main "lambdacd.prod"
                                        :asset-path   "js-gen/out"
                                        :jar true
                                        :optimizations :advanced
                                        :pretty-print  false}}}}
  :profiles {:release  {:hooks [leiningen.cljsbuild]}
             ;; the namespace for all the clojurescript-dependencies,
             ;; we don't want them as dependencies of the final library as cljs is already compiled then
             :provided {:dependencies [[bidi "1.18.7"]
                                       [cljs-ajax "0.3.10"]
                                       [re-frame "0.4.1"]
                                       [reagent "0.5.1"]
                                       [reagent-utils "0.1.8"]
                                       [com.andrewmcveigh/cljs-time "0.3.5"]
                                       [cljsjs/ansiparse "0.0.5-1-0"]
                                       [org.clojure/clojurescript "1.7.48"]]}
             :dev      {:main         todopipeline.pipeline
                        :repl-options {:nrepl-middleware [cemerick.piggieback/wrap-cljs-repl]}
                        :dependencies [[org.clojure/core.match "0.3.0-alpha4"]
                                       [ring-mock "0.1.5"]
                                       [org.clojars.runa/conjure "2.2.0"]
                                       [prismatic/dommy "1.0.0"]
                                       [com.gearswithingears/shrubbery "0.4.1"]
                                       [http-kit "2.1.19"]
                                       [ring-server "0.3.1"]
                                       [ring/ring-devel "1.3.2"]
                                       [leiningen "2.5.1"]
                                       [figwheel "0.4.0"]
                                       [weasel "0.7.0"]
                                       [lein-doo "0.1.4"]
                                       [com.cemerick/piggieback "0.2.1"]
                                       [org.clojure/tools.nrepl "0.2.12"]
                                       [pjstadig/humane-test-output "0.6.0"]
                                       [ch.qos.logback/logback-core "1.0.13"]
                                       [ch.qos.logback/logback-classic "1.0.13"]]

                        :source-paths ["env/dev/clj"]
                        :plugins      [[lein-figwheel "0.4.0"]
                                       [com.cemerick/clojurescript.test "0.3.3"]]

                        :injections   [(require 'pjstadig.humane-test-output)
                                       (pjstadig.humane-test-output/activate!)]

                        :figwheel     {:http-server-root "public"
                                       :server-port      3449
                                       :css-dirs         ["resources/public/css"]}

                        :env          {:dev? true}
                        :cljsbuild    {:builds        {:app {:source-paths ["visual-styleguide/src/cljs" "env/dev/cljs" "src/cljs"]
                                                             :compiler     {:main          "lambdacd.dev"
                                                                            :optimizations :none
                                                                            :source-map    true}}
                                                       :test {:source-paths ["src/cljs" "test/cljs"]
                                                              :compiler     {:optimizations :none
                                                                             :main "lambdacd.runner"
                                                                             :pretty-print  true}}}}}})
