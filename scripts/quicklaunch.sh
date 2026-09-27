#!/usr/bin/env bash
# SPDX-FileCopyrightText: 2026 5thlayer
# SPDX-License-Identifier: MIT
#
# Launch the dev client into the most recent save in run/saves, unless one is already running.
#
#   scripts/quicklaunch.sh [save name]
#
# The game is detached and its output goes to $QUICKLAUNCH_LOG (default: a temp file, printed).
# The script returns once the client is up, or fails if the build does.
set -euo pipefail
cd "$(dirname "$0")/.."

# Each client of this checkout reads this file. A second one fights the first over run/, and a
# world opened in both is corrupted.
args_file="$PWD/build/moddev/clientRunProgramArgs.txt"
if pgrep -f "net.neoforged.devlaunch.Main.*@$args_file" > /dev/null; then
    echo "quicklaunch: a client is already running; close the game first." >&2
    exit 1
fi

save="${1:-}"
if [[ -z "$save" ]]; then
    latest="$(ls -t run/saves/*/level.dat 2>/dev/null | head -1 || true)"
    [[ -n "$latest" ]] && save="$(basename "$(dirname "$latest")")"
fi

log="${QUICKLAUNCH_LOG:-$(mktemp -t quicklaunch).log}"
props=()
if [[ -n "$save" ]]; then
    props=("-PquickPlay=$save")
else
    echo "quicklaunch: no save found; launching to the menu." >&2
fi

nohup sh ./gradlew runClient ${props[@]+"${props[@]}"} > "$log" 2>&1 &
gradle=$!

for _ in $(seq 1 300); do
    grep -aq "Sound engine started" "$log" && break
    if ! kill -0 "$gradle" 2> /dev/null; then
        grep -a -A5 "What went wrong" "$log" >&2 || tail -20 "$log" >&2
        echo "quicklaunch: the client did not start; log: $log" >&2
        exit 1
    fi
    sleep 1
done

echo "client up${save:+, opening \"$save\"}; log: $log"
