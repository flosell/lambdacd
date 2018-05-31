#!/bin/bash

set -e

# We have tests that deal with git. They need to have this set to pass
git config --global user.email "you@example.com"
git config --global user.name "Your Name"
