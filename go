#!/bin/bash

set -e

SCRIPT_DIR=$(dirname "$0")

bold="\033[1m"
normal="\033[0m"

checkmark="\xe2\x98\x91"
cross="\xe2\x98\x92"

startred='\033[0;31m'
endcolor='\033[0m'
startgreen='\033[0;32m'

echoCheck() {
  echo -e "$startgreen $checkmark $1 $endcolor"
}

echoError() {
 echo -e "$startred $cross $1 $endcolor"
}

echob() {
  echo -e "${bold}$*${normal}"
}

goal_setupTodopipelineEnv() {
  check "vagrant"

  if [ "$(vagrant status | grep running)" == "" ]; then
    vagrant up
  fi

  vagrant ssh-config frontend_ci >> /tmp/lambdacd-dev-env-ssh-config
  vagrant ssh-config backend_ci >> /tmp/lambdacd-dev-env-ssh-config

  mkdir -p /tmp/mockrepo

  goal_setup
}

setupNPM() {
  echob "Installing NPM dependencies..."
  npm install
}

setupKarma() {
  npm i -g karma-cli
}

buildCljsOnce() {
 lein cljsbuild once
}

check() {
  if ! type "$1" > /dev/null 2>&1; then
    echoError "$2"
    exit 1
  else
    echoCheck "$3"
  fi
}

checkRequirement() {
  check "lein" "Leiningen not installed, go to http://leiningen.org/ for details" "Leiningen installed"
  check "npm" "NPM not installed, go to https://nodejs.org/en/ for details" "NPM installed"
  echo
}

goal_deps() {
  checkRequirement

  setupNPM
  setupKarma
  lein deps

  echo
  echob "[SUCCESS] deps are installed."
}

goal_setup() {
  goal_deps

  buildCss
  buildCljsOnce

  echo
  echob "[SUCCESS] You are good to go!"
  echo "Call ./go serve to start a demo pipeline."
}

goal_test-clj() {
  echob "Running tests for clojure code..."
  lein test :all
}

goal_test-cljs() {
  echob "Running tests for clojure script code..."
  lein doo chrome-headless test once
}

goal_test-cljs-auto() {
  lein doo chrome-headless test auto
}

goal_test() {
  goal_test-clj && goal_test-cljs
}

goal_test-clj-unit() {
  lein test
}

goal_test-cljs-unit-repeat() {
  n=0
  echob "Repeating unit tests a couple of times to get a higher likelihood of flaky tests failing."
  echob "If you got here, you can assume that your unit tests are all succeeding, we are just checking for flakiness now"
  until [ $n -ge 10 ]
  do
    goal_test-clj-unit || exit 1
    n=$[$n+1]
  done
  echob "Tests didn't fail in a few tries, maybe nothing is flaky."
}

goal_check-style() {
  echob "Running code-style checks..."
  # kibit can't handle namespaced keywords, removing this output https://github.com/jonase/kibit/issues/14
  lein kibit
}

goal_clean() {
  lein clean
  rm -f resources/public/css/*.css
}

checkGPG() {
  if ! echo foo | gpg -ab --batch > /dev/null; then
    echoError "GPG not set up properly"
    exit 1
  fi
}

goal_release() {
  checkGPG && goal_test && goal_clean && buildCss && lein with-profile +release release $1 && scripts/github-release.sh &&  goal_publish-api-doc $(chag latest)
}

goal_release-local() {
  buildCss && lein with-profile +release install
}

goal_push() {
  goal_test && goal_check-style && git push
}

goal_serve() {
  lein run
}

goal_serve-cljs() {
  lein figwheel app
}

goal_serve-css() {
  npm run build:watch
}

buildCss() {
  echob "Building CSS from LESS..."
  npm run build
}

goal_repl-server() {
  lein repl :headless :port 58488
}

goal_generate-howto-toc() {
  check "gh-md-toc" "Make sure gh-md-toc is in PATH or download from https://github.com/ekalinin/github-markdown-toc.go/releases"
  gh-md-toc ${SCRIPT_DIR}/doc/howto.md
}

goal_generate-api-doc() {
   lein codox
}

goal_publish-api-doc() {
    DOC_LABEL="$1"
    DOC_DIR="api-docs/${DOC_LABEL}"

    if [ -z "${DOC_LABEL}" ]; then
        echob "need to set doc-label"
        exit 1
    fi

    rm -rf gh-pages-api-doc-release

    git clone --single-branch --depth 1 --branch gh-pages git@github.com:flosell/lambdacd.git gh-pages-api-doc-release
    rm -rf gh-pages-api-doc-release/${DOC_DIR}
    mkdir -p gh-pages-api-doc-release/${DOC_DIR}

    cp -R ${SCRIPT_DIR}/target/doc/ gh-pages-api-doc-release/${DOC_DIR}

    pushd gh-pages-api-doc-release/api-docs > /dev/null

    rm -rf latest
    ln -s ${DOC_LABEL} latest

    (
      echo "version"; # header
      for i in $(find . -depth 1 -type d -or -type l | sort --reverse   ); do
        echo $(basename ${i});
      done;
    ) > ../_data/apidoc-versions.csv

    git add ${DOC_LABEL} latest ../_data/apidoc-versions.csv
    git commit -m "Update generated API Doc for ${DOC_LABEL}"
    git push origin gh-pages

    popd > /dev/null
}

goal_check-dependencies() {
     lein nvd check
     npm audit
}

goal_fix-dependencies() {
     npm audit fix
}

if type -t "goal_$1" &>/dev/null; then
  goal_$1 ${@:2}
else
  echo "usage: $0 <goal>

goal:
    clean                -- clear all build artifacts
    check-dependencies   -- check dependencies for known vulnerabilities
    deps                 -- install all dependencies
    setup                -- to set up your environment
    test                 -- run all tests
    test-clj             -- run all tests for the clojure-part
    test-clj-unit        -- run only unit tests for the clojure-part
    test-clj-unit-repeat -- run unit tests for the clojure-part ten times to increase likelihood of flaky tests failing
    test-cljs            -- run all ClojureScript tests (i.e. unit tests for frontend)
    test-cljs-auto       -- starts autotest-session for frontend
    check-style          -- runs code-style checks
    serve                -- start a server with a demo-pipeline
    serve-cljs           -- compile clojurescript and watch for changes
    serve-css            -- autocompile and autoprefix css
    push                 -- run all tests and push current state
    release              -- release current version
    release-local        -- install current version in local repository
    repl-server          -- start a repl cursive can use to run tests in
    setupTodopipelineEnv -- setup everything you need to make the demo pipeline green
    generate-howto-toc   -- generate table of contents for howto
    generate-api-doc     -- generate api documentation
    publish-api-doc      -- publish api documentation to github pages"
    exit 1
fi
