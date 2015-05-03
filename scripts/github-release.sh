#!/usr/bin/env bash

set -e

# TODO: make sure the following is installed:
# https://github.com/aktau/github-release
# https://github.com/mtdowling/chag
# $GITHUB_TOKEN is set

SCRIPT_DIR=$(dirname "$0")
cd ${SCRIPT_DIR}/..

VERSION=$(chag latest)
CHANGELOG=$(chag contents)

echo "Publishing Release to GitHub: "
echo "Version ${VERSION}"
echo "${CHANGELOG}"
echo

github-release release \
    --user flosell \
    --repo lambdacd \
    --tag ${VERSION} \
    --name ${VERSION} \
    --description "${CHANGELOG}"

echo "Published release"