#!/bin/bash 

set -e

setup() {
  if [ "$(vagrant status | grep running)" == "" ]; then 
    vagrant up 
  fi

  vagrant ssh-config frontend_ci >> /tmp/lambdacd-dev-env-ssh-config
  vagrant ssh-config backend_ci >> /tmp/lambdacd-dev-env-ssh-config

  mkdir -p /tmp/mockrepo
  echo "[SUCCESS] You are good to go"
}

testallClojure() {
  lein test :all
}

testallClojureScript() {
  lein cljsbuild test
}

autotestClojureScript() {
  lein cljsbuild auto test
}


testall() {
    testallClojure
    testallClojureScript
}

testunit() {
  lein test
}

release() {
  testall
  lein clear
  lein deploy clojars
}

push() {
  testall && git push
}

serve() {
  lein ring server
}

serveClojureScript() {
  lein figwheel app
}

if [ "$1" == "setup" ]; then
    setup
elif [ "$1" == "testall" ]; then
    testall
elif [ "$1" == "test" ]; then
    testunit
elif [ "$1" == "testcljs" ]; then
    testallClojureScript
elif [ "$1" == "autoTestCljs" ]; then
    autotestClojureScript
elif [ "$1" == "release" ]; then
    release
elif [ "$1" == "push" ]; then
    push
elif [ "$1" == "serve" ]; then
    serve
elif [ "$1" == "servecljs" ]; then
    serveClojureScript
else
    echo "usage: $0 <goal>

goal:
    setup        -- to set up your environment
    test         -- run unit tests
    testall      -- run all tests
    testcljs     -- run all ClojureScript tests (i.e. unit tests for frontend)
    autotestCljs -- starts autotest-session for frontend
    serve        -- start a server with a demo-pipeline
    servecljs    -- compile clojurescript and watch for changes
    push         -- run all tests and push current state
    release      -- release current version"
    exit 1
fi
