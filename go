#!/bin/bash
set -e

goal_serve() {
  jekyll serve --drafts
}

if type -t "goal_$1" &>/dev/null; then
  goal_$1 ${@:2}
else
  echo "usage: $0 <goal>

goal:
    serve              -- start a development server for preview (including drafts)
"
  exit 1
fi
