(def clojure-version-to-use (or
                       (System/getenv "CLOJURE_VERSION")
                       "1.8.0"))

(defproject lambdacd "0.14.2-SNAPSHOT"
  :description "A library to create a continous delivery pipeline in code."
  :url "http://github.com/flosell/lambdacd"
  :license {:name "Apache License, version 2.0"
            :url "http://www.apache.org/licenses/LICENSE-2.0.html"}
  :min-lein-version "2.5.0"
  :deploy-repositories [["clojars" {:creds :gpg}]
                        ["releases" :clojars]]
  :repositories [["gocd" "https://dl.bintray.com/gocd-maven-repo/generic/gocd"]] ; for jezhumbles javasysmon
  :source-paths ["src/clj" "src/cljs"]
  :test-paths ["test/clj" "example/clj"]
  :jar-exclusions [#"logback.xml"]
  :dependencies [[org.clojure/clojure ~clojure-version-to-use]
                 [throttler "1.0.0"]
                 [hiccup "1.0.5"]
                 [org.clojure/data.json "0.2.6"]
                 [me.raynes/conch "0.8.0"]
                 [me.raynes/fs "1.4.6"]
                 [org.clojure/core.async "0.4.474"]
                 [compojure "1.6.1"]
                 [org.clojure/tools.logging "0.3.1"]
                 [org.slf4j/slf4j-api "1.7.21"]
                 [ring/ring-json "0.3.1"]
                 [cheshire "5.4.0"]
                 [cljsjs/moment "2.22.2-0"]
                 [clj-time "0.9.0"]
                 [com.jezhumble/javasysmon "0.3.6"]
                 [clj-timeframes "0.1.0"]]
  ; excluding a few transitive dependencies:
  ; process-tree-killer depends on this for windows only and doesnt provide it...
  ; xz is a dependency of fs, contains a vulnerability and we don't really use it
  :exclusions [org.jvnet.winp/winp
               org.tukaani/xz]
  :test-selectors {:default (complement :smoke)
                   :smoke :smoke
                   :all (constantly true)}

  :codox {:namespaces [#"^(?!.*\.internal\.).*$"]
          :doc-files []
          :source-uri "https://github.com/flosell/lambdacd/blob/master/{filepath}#L{line}"
          :project    {:name "LambdaCD"}
          :themes [:rdash]
          :metadata   {:doc/format :markdown}}

  :plugins [[lein-codox "0.10.2"]
            [lein-cljsbuild "1.1.7"]
            [lein-doo "0.1.10"]
            [lein-environ "1.0.2"]
            [lein-kibit "0.1.6-beta1"]
            [quickie "0.3.6"]]

  :clean-targets ^{:protect false} [:target-path :compile-path "resources/public/js-gen"]

  :cljsbuild {:builds {:app {:source-paths ["src/cljs" "env/prod/cljs"]
                             :compiler {:output-to "resources/public/js-gen/app.js"
                                        :output-dir "resources/public/js-gen/out"
                                        :main "lambdacd.prod"
                                        :asset-path "js-gen/out"
                                        :optimizations :advanced
                                        :pretty-print false}}}}
  :profiles {:release  {:hooks [leiningen.cljsbuild]
                        :release-tasks [["vcs" "assert-committed"]
                                        ["change" "version" "leiningen.release/bump-version" "release"]
                                        ["vcs" "commit"]
                                        ["vcs" "tag"]
                                        ["deploy"]
                                        ["codox"]
                                        ["change" "version" "leiningen.release/bump-version"]
                                        ["vcs" "commit"]
                                        ["vcs" "push"]]}
             ;; the namespace for all the clojurescript-dependencies,
             ;; we don't want them as dependencies of the final library as cljs is already compiled then
             :provided {:dependencies [[bidi "1.18.7"]
                                       [cljs-ajax "0.7.3"]
                                       [re-frame "0.10.5"]
                                       [reagent "0.8.1"]
                                       [reagent-utils "0.3.1"]
                                       [com.andrewmcveigh/cljs-time "0.5.2"]
                                       [cljsjs/ansiparse "0.0.5-1-0"]
                                       [org.clojure/clojurescript "1.10.238"]
                                       ; protobuf is a transitive dependency of clojurescript.
                                       ; pinning here since the default is currently a vulnerable version (even though the vulnerability shouldn't affect us)
                                       [com.google.protobuf/protobuf-java "3.5.1"]]}
             :dev      {:main         todopipeline.pipeline
                        :repl-options {:nrepl-middleware [cemerick.piggieback/wrap-cljs-repl]}
                        :dependencies [[org.clojure/core.match "0.3.0-alpha4"]
                                       [ring-mock "0.1.5"]
                                       [codox-theme-rdash "0.1.1"]
                                       [org.clojars.runa/conjure "2.2.0"]
                                       [prismatic/dommy "1.0.0"]
                                       [com.gearswithingears/shrubbery "0.4.1"]
                                       [http-kit "2.3.0"]
                                       [figwheel "0.5.16"]
                                       [weasel "0.7.0"]
                                       [lein-doo "0.1.4"]
                                       [com.cemerick/piggieback "0.2.1"]
                                       [org.clojure/tools.nrepl "0.2.12"]
                                       [pjstadig/humane-test-output "0.6.0"]
                                       [ch.qos.logback/logback-core "1.2.3"]
                                       [ch.qos.logback/logback-classic "1.2.3"]]

                        :source-paths ["env/dev/clj"]
                        :plugins      [[lein-figwheel "0.5.16"]
                                       [lein-nvd "0.5.3"]]
                        :nvd {:suppression-file "suppression.xml"}

                        :injections   [(require 'pjstadig.humane-test-output)
                                       (pjstadig.humane-test-output/activate!)]

                        :figwheel     {:http-server-root "public"
                                       :server-port      3449
                                       :css-dirs         ["resources/public/css"]}

                        :env          {:dev? true}
                        :cljsbuild    {:builds {:app {:source-paths ["visual-styleguide/src/cljs" "env/dev/cljs" "src/cljs"]
                                                      :compiler     {:main "lambdacd.dev"
                                                                     :output-to "resources/public/js-gen/app.js"
                                                                     :output-dir "resources/public/js-gen/out"
                                                                     :asset-path "js-gen/out"
                                                                     :optimizations :none
                                                                     :source-map true}}
                                                :test {:source-paths ["src/cljs" "test/cljs"]
                                                       :compiler     {:main "lambdacd.runner"
                                                                      :output-to "resources/public/test/test.js"
                                                                      :output-dir "resources/public/test"
                                                                      :asset-path "js-gen/out"
                                                                      :optimizations :none
                                                                      :pretty-print  true}}}}}})
