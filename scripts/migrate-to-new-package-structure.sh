#!/usr/bin/env bash

function replaceInClj {
    find . -name '*.clj' -print0 | xargs -0 sed -i '' -e "s/$1/$2/g"
}

replaceInClj 'lambdacd\.manualtrigger' 'lambdacd\.steps\.manualtrigger'
replaceInClj 'lambdacd\.control-flow' 'lambdacd\.steps\.control-flow'
replaceInClj 'lambdacd\.git' 'lambdacd\.steps\.git'
replaceInClj 'lambdacd\.shell' 'lambdacd\.steps\.shell'