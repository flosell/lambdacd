#!/bin/bash

setup() {
  if [ "$(vagrant status | grep running)" == "" ]; then 
    vagrant up 
  fi

  vagrant ssh-config frontend_ci >> /tmp/lambdacd-dev-env-ssh-config
  vagrant ssh-config backend_ci >> /tmp/lambdacd-dev-env-ssh-config
}

if [ "$1" == "setup" ]; then
    setup
else
    echo "usage: $1 setup -- to set up your environment"
fi