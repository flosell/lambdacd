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

testall() {
  lein test :all
}

testunit() {
  lein test
}

release() {
  lein publish clojars
}

push() {
  testall && git push
}

if [ "$1" == "setup" ]; then
    setup
elif [ "$1" == "testall" ]; then
    testall
elif [ "$1" == "test" ]; then
    testunit
elif [ "$1" == "release" ]; then
    release
elif [ "$1" == "push" ]; then
    push
else
    echo "usage: $0 <goal>

goal:
    setup    -- to set up your environment
    test     -- run unit tests
    testall  -- run all tests
    push     -- run all tests and push current state
    release  -- release current version"
    exit 1
fi
