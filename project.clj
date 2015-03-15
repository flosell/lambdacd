(defproject lambdacd "0.1.0-alpha14-SNAPSHOT"
  :description "a library to create a continous delivery pipeline in code"
  :url "http://github.com/flosell/lambdacd"
  :license {:name "Apache License, version 2.0"
            :url "http://www.apache.org/licenses/LICENSE-2.0.html"}
  :min-lein-version "2.5.0"
  :deploy-repositories [["clojars" {:creds :gpg}]
                        ["releases" :clojars]]
  :source-paths ["src/clj" "src/cljs"]
  :test-paths ["test/clj"]
  :dependencies [[org.clojure/clojure "1.6.0"]
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

                 [cljsjs/react "0.12.2-5"]
                 [reagent "0.5.0-alpha3"]
                 [reagent-utils "0.1.2"]
                 [secretary "1.2.1"]

                 [prone "0.8.0"]
                 [selmer "0.8.0"]
                 [environ "1.0.0"]

                 [org.clojure/clojurescript "0.0-2850" :scope "provided"]

                 [hiccup "1.0.5"]]
  :test-selectors {:default (complement :smoke)
                   :smoke :smoke
                   :all (constantly true)}
  :ring {:handler todopipeline.pipeline/app
         :init todopipeline.pipeline/start-pipeline-thread }
  :main todopipeline.pipeline
  :plugins [
            [lein-cljsbuild "1.0.5"]
            [lein-environ "1.0.0"]
            [lein-asset-minifier "0.2.2"]
            [lein-ring "0.8.13"]
            [lein-kibit "0.0.8"]
            [lein-marginalia "0.8.0"]
            [quickie "0.3.6"]]
  :clean-targets ^{:protect false} ["resources/public/old/js-gen"]

  :minify-assets
  {:assets
   {"resources/public/old/css/site.min.css" "resources/public/old/css/site.css"}}

  :cljsbuild {:builds {:app {:source-paths ["src/cljs"]
                             :compiler {:output-to     "resources/public/old/js-gen/app.js"
                                        :output-dir    "resources/public/old/js-gen/out"
                                        ;;:externs       ["react/externs/react.js"]
                                        :asset-path   "js-gen/out"
                                        :optimizations :none
                                        :pretty-print  true}}}}

  :profiles {:dev {:repl-options {:init-ns lambdacd.handler
                                  :nrepl-middleware [cemerick.piggieback/wrap-cljs-repl]}

                   :dependencies [[ring-mock "0.1.5"]
                                  [http-kit "2.1.16"]
                                  [ring/ring-devel "1.3.2"]
                                  [leiningen "2.5.1"]
                                  [figwheel "0.2.5-SNAPSHOT"]
                                  [weasel "0.6.0-SNAPSHOT"]
                                  [com.cemerick/piggieback "0.1.6-SNAPSHOT"]
                                  [pjstadig/humane-test-output "0.6.0"]]

                   :source-paths ["env/dev/clj"]
                   :plugins [[lein-figwheel "0.2.3-SNAPSHOT"]
                             [com.cemerick/clojurescript.test "0.3.3"]]

                   :injections [(require 'pjstadig.humane-test-output)
                                (pjstadig.humane-test-output/activate!)]

                   :figwheel {:http-server-root "public"
                              :server-port 3449
                              :css-dirs ["resources/public/old/css"]}

                   :env {:dev? true}

                   :cljsbuild {:builds {:app {:source-paths ["env/dev/cljs"]
                                              :compiler {   :main "lambdacd.dev"
                                                         :source-map true}}
                                        :test {:source-paths ["src/cljs"  "test/cljs"]
                                               :compiler {:output-to "target/test.js"
                                                          :optimizations :whitespace
                                                          :pretty-print true
                                                          :preamble ["react/react.js"]}}}
                               :test-commands {"unit" ["phantomjs" :runner
                                                       "test/vendor/es5-shim.js"
                                                       "test/vendor/es5-sham.js"
                                                       "test/vendor/console-polyfill.js"
                                                       "target/test.js"]}}}

             :uberjar {:hooks [leiningen.cljsbuild minify-assets.plugin/hooks]
                       :env {:production true}
                       :aot :all
                       :omit-source true
                       :cljsbuild {:jar true
                                   :builds {:app
                                            {:source-paths ["env/prod/cljs"]
                                             :compiler
                                                           {:optimizations :advanced
                                                            :pretty-print false}}}}}

             :production {:ring {:open-browser? false
                                 :stacktraces?  false
                                 :auto-reload?  false}
                          :cljsbuild {:builds {:app {:compiler {:main "lambdacd.prod"}}}}
                          }})