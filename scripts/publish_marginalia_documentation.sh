#!/bin/bash
# Script to publish a marginalia documentation of the todopipeline to github pages.


git co gh-pages

git show master:doc/uberdoc.html > marginalia/uberdoc.html
git add marginalia/uberdoc.html
git commit -m "updated uberdoc"

git co master