#!/bin/sh
# SPDX-FileCopyrightText: 2026 5thlayer
# SPDX-License-Identifier: MIT
#
# Reads a Module's `libworksUpstreams` lines, `<repo> <tag>`, on stdin and publishes each
# upstream Library to ~/.m2 from its tag, as its release does on a maintainer's machine.
# LIBWORKS_GIT_BASE names where the repos live, https://github.com by default.
set -eu
base=${LIBWORKS_GIT_BASE:-https://github.com}
while read -r repo tag; do
    [ -n "$repo" ] || continue
    dir=".upstreams/$repo"
    echo "Building $repo from $tag"
    git clone -q --depth 1 --branch "$tag" "$base/$repo" "$dir"
    (cd "$dir" && ./gradlew publishToMavenLocal)
done
