#!/usr/bin/env bash

function replaceInClj {
    find . -name '*.clj' -print0 | xargs -0 sed -i '' -e "s/$1/$2/g"
}

replaceInClj 'lambdacd\.manualtrigger' 'lambdacd\.steps\.manualtrigger'
replaceInClj 'lambdacd\.control-flow' 'lambdacd\.steps\.control-flow'
replaceInClj 'lambdacd\.git' 'lambdacd\.steps\.git'
replaceInClj 'lambdacd\.shell' 'lambdacd\.steps\.shell'

replaceInClj 'lambdacd\.json-model' 'lambdacd\.pipeline-state-persistence'

replaceInClj 'lambdacd\.presentation([^.])' 'lambdacd\.presentation\.pipeline-structure$1'

replaceInClj 'lambdacd\.execution' 'lambdacd\.internal\.execution'

replaceInClj 'lambdacd.pipeline-state' 'lambdacd.internal.pipeline-state'

replaceInClj 'lambdacd.new-ui' 'lambdacd.ui.new_ui'
replaceInClj 'lambdacd.server' 'lambdacd.ui.ui-server'