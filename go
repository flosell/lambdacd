#!/bin/bash 

set -e

bold=$(tput bold)
normal=$(tput sgr0)

echob() {
  echo "${bold}$*${normal}"
}
setup() {
  if [ "$(vagrant status | grep running)" == "" ]; then 
    vagrant up 
  fi

  vagrant ssh-config frontend_ci >> /tmp/lambdacd-dev-env-ssh-config
  vagrant ssh-config backend_ci >> /tmp/lambdacd-dev-env-ssh-config

  mkdir -p /tmp/mockrepo

  npm install
  buildCss

  echo "[SUCCESS] You are good to go"
}

testallClojure() {
  echob "Running tests for clojure code..."
  lein test :all
}

testallClojureScript() {
  echob "Running tests for clojure script code..."
  lein cljsbuild test
}

autotestClojureScript() {
  lein cljsbuild auto test
}


testall() {
  testallClojure && testallClojureScript
}

testunit() {
  lein test
}

check-style() {
  echob "Running code-style checks..."
  lein kibit
}
clean() {
  lein clean
  rm -f resources/public/css/*.css
}

release() {
  testall && clean && buildCss && lein with-profile +release release $1 && scripts/github-release.sh
}
releaseLocal() {
  buildCss && lein with-profile +release install
}

push() {
  testall && check-style && git push
}

serve() {
  lein run
}

serveClojureScript() {
  lein figwheel app
}

serveCss() {
  npm run build:watch
}

buildCss() {
  npm run build
}

repl-server() {
  lein repl :headless :port 58488
}

if [ "$1" == "clean" ]; then
    clean
elif [ "$1" == "setup" ]; then
    setup
elif [ "$1" == "test" ]; then
    testall
elif [ "$1" == "test-clj" ]; then
    testallClojure
elif [ "$1" == "test-clj-unit" ]; then
    testunit
elif [ "$1" == "test-cljs" ]; then
    testallClojureScript
elif [ "$1" == "test-cljs-auto" ]; then
    autotestClojureScript
elif [ "$1" == "check-style" ]; then
    check-style
elif [ "$1" == "release" ]; then
    release $2
elif [ "$1" == "release-local" ]; then
    releaseLocal
elif [ "$1" == "push" ]; then
    push
elif [ "$1" == "serve" ]; then
    serve
elif [ "$1" == "serve-cljs" ]; then
    serveClojureScript
elif [ "$1" == "serve-css" ]; then
    serveCss
elif [ "$1" == "repl-server" ]; then
    repl-server
else
    echo "usage: $0 <goal>

goal:
    clean          -- clear all build artifacts
    setup          -- to set up your environment
    test           -- run all tests
    test-clj       -- run all tests for the clojure-part
    test-clj-unit  -- run only unit tests for the clojure-part
    test-cljs      -- run all ClojureScript tests (i.e. unit tests for frontend)
    test-cljs-auto -- starts autotest-session for frontend
    check-style    -- runs code-style checks
    serve          -- start a server with a demo-pipeline
    serve-cljs     -- compile clojurescript and watch for changes
    serve-css      -- autocompile and autoprefix css
    push           -- run all tests and push current state
    release        -- release current version
    release-local  -- install current version in local repository
    repl-server    -- start a repl cursive can use to run tests in"
    exit 1
fi
