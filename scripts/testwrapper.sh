#!/usr/bin/env bash
# pipefail so we can detect the error
set -o pipefail

# remove last argument, leiningen passes in the message from the compiler...
set -- "${@:1:$(($#-1))}"

function notify() {
    if which osascript > /dev/null; then
        if [ "$3" == "error" ]; then
            sound="sound name \"Basso\""
        fi
        osascript -e "display notification \"$2\" with title \"$1\" $sound"
    else
        echo "$1: $2"
    fi
}

$@ | tee /tmp/testout

if [ $? -ne 0 ]; then
    error="error"
    echo "errored"
fi

notify "Test run" "$(cat /tmp/testout | tail -n 1)" ${error}