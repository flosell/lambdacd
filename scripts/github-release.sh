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
USER="flosell"
REPO="lambdacd"

echo "Publishing Release to GitHub: "
echo "Version ${VERSION}"
echo "${CHANGELOG}"
echo

github-release release \
    --user ${USER} \
    --repo ${REPO} \
    --tag ${VERSION} \
    --name ${VERSION} \
    --description "${CHANGELOG}"

echo "Published release"