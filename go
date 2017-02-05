#!/bin/bash

set -e

SCRIPT_DIR=$(dirname "$0")

bold=$(tput bold)
normal=$(tput sgr0)

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

setupTodopipelineEnv() {
  check "vagrant"

  if [ "$(vagrant status | grep running)" == "" ]; then
    vagrant up
  fi

  vagrant ssh-config frontend_ci >> /tmp/lambdacd-dev-env-ssh-config
  vagrant ssh-config backend_ci >> /tmp/lambdacd-dev-env-ssh-config

  mkdir -p /tmp/mockrepo

  setup
}

setupNPM() {
  echob "Installing NPM dependencies..."
  npm install
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
setup() {
  checkRequirement

  setupNPM
  buildCss
  buildCljsOnce

  echo
  echob "[SUCCESS] You are good to go!"
  echo "Call ./go serve to start a demo pipeline."
}

testallClojure() {
  echob "Running tests for clojure code..."
  lein test :all
}

testallClojureScript() {
  echob "Running tests for clojure script code..."
  lein doo phantom test once
}

autotestClojureScript() {
  lein doo phantom test auto
}


testall() {
  testallClojure && testallClojureScript
}

testunit() {
  lein test
}

testunitRepeat() {
  n=0
  echob "Repeating unit tests a couple of times to get a higher likelihood of flaky tests failing."
  echob "If you got here, you can assume that your unit tests are all succeeding, we are just checking for flakiness now"
  until [ $n -ge 10 ]
  do
    testunit || exit 1
    n=$[$n+1]
  done
  echob "Tests didn't fail in a few tries, maybe nothing is flaky."
}
check-style() {
  echob "Running code-style checks..."
  # kibit can't handle namespaced keywords, removing this output https://github.com/jonase/kibit/issues/14
  lein kibit | sed -e '/Check failed -- skipping rest of file/,/java.lang.RuntimeException: Invalid token: ::/d' | tee /tmp/kibit-output
  [[ ! $(cat /tmp/kibit-output)  ]]
}
clean() {
  lein clean
  rm -f resources/public/css/*.css
}

checkGPG() {
  if ! echo foo | gpg -ab --batch > /dev/null; then
    echoError "GPG not set up properly"
    exit 1
  fi
}
release() {
  checkGPG && testall && clean && buildCss && lein with-profile +release release $1 && scripts/github-release.sh
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
  echob "Building CSS from LESS..."
  npm run build
}

repl-server() {
  lein repl :headless :port 58488
}

generate-howto-toc() {
  check "gh-md-toc" "Make sure gh-md-toc is in PATH or download from https://github.com/ekalinin/github-markdown-toc.go/releases"
  gh-md-toc ${SCRIPT_DIR}/doc/howto.md
}

generate-api-documentation() {
   lein codox
}

publish-api-doc() {
set -x
    DOC_LABEL="${TRAVIS_TAG:-${TRAVIS_BRANCH}}"
    DOC_DIR="api-docs/${DOC_LABEL}"

    if [ -z "${DOC_LABEL}" ]; then
        echob "Building neither branch nor tag, not publishing apid docs"
        exit 1
    fi

    rm -rf gh-pages-api-doc-release
#    git clone --branch gh-pages "https://flosell:${GITHUB_API_KEY}@github.com/flosell/lambdacd" gh-pages-api-doc-release
    git clone --single-branch --depth 1 --branch gh-pages git@github.com:flosell/lambdacd.git gh-pages-api-doc-release
    rm -rf gh-pages-api-doc-release/${DOC_DIR}
    mkdir -p gh-pages-api-doc-release/${DOC_DIR}

    cp -R ${SCRIPT_DIR}/target/doc/ gh-pages-api-doc-release/${DOC_DIR}

    pushd gh-pages-api-doc-release > /dev/null

    git add ${DOC_DIR}
    git commit -m "Update generated API Doc for ${DOC_LABEL}"
    git push origin gh-pages

    popd > /dev/null
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
elif [ "$1" == "setupTodopipelineEnv" ]; then
    setupTodopipelineEnv
elif [ "$1" == "generate-howto-toc" ]; then
    generate-howto-toc
elif [ "$1" == "generate-api-doc" ]; then
    generate-api-documentation
elif [ "$1" == "publish-api-doc" ]; then
    publish-api-doc
elif [ "$1" == "test-clj-unit-repeat" ]; then
    testunitRepeat
else
    echo "usage: $0 <goal>

goal:
    clean                -- clear all build artifacts
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
