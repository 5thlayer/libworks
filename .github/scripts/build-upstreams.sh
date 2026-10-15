#!/bin/sh
# SPDX-FileCopyrightText: 2026 5thlayer
# SPDX-License-Identifier: MIT
#
# Reads a Module's `libworksUpstreams` lines, `<repo> <tag>`, on stdin and publishes each upstream
# Module to ~/.m2 from its tag, as its release does on a maintainer's machine. An upstream that
# applies the libworks plugin has the plugin version it applies, then its own upstreams, built first,
# recursively; each repo and tag is built once. An upstream not on the plugin is published as is.
# A line `<repo> <tag> <project>`, such as `5thlayer/libworks runtime-v0.1.0 :runtime`, publishes
# only that Gradle project from the tag, and is not recursed into.
# LIBWORKS_GIT_BASE names where the repos live, https://github.com by default; MAVEN_REPO_LOCAL
# the local maven repository, ~/.m2/repository by default.
set -eu
base=${LIBWORKS_GIT_BASE:-https://github.com}
m2=${MAVEN_REPO_LOCAL:-$HOME/.m2/repository}
mkdir -p .upstreams
built="$PWD/.upstreams/built"
: > "$built"

# The libworks plugin at <version>, from its tag, unless ~/.m2 has it.
plugin() (
    [ ! -d "$m2/io/github/5thlayer/libworks-plugin/$1" ] || exit 0
    dir="$PWD/.upstreams/5thlayer/libworks/plugin-v$1"
    [ -d "$dir" ] && exit 0
    echo "Building the libworks plugin $1 from plugin-v$1"
    git clone -q --depth 1 --branch "plugin-v$1" "$base/5thlayer/libworks" "$dir"
    cd "$dir" && ./gradlew -p plugin publishToMavenLocal
)

# Runs in a subshell, so a recursive call keeps its caller's variables.
upstream() (
    repo=$1 tag=$2 project=${3:-}
    ! grep -qxF "$repo $tag" "$built" || exit 0
    echo "$repo $tag" >> "$built"
    dir="$PWD/.upstreams/$repo/$tag"
    echo "Building $repo${project:+ $project} from $tag"
    git clone -q --depth 1 --branch "$tag" "$base/$repo" "$dir"
    if [ -n "$project" ]; then
        cd "$dir" && exec ./gradlew "$project:publishToMavenLocal"
    fi
    version=$(sed -n "s/^ *id 'io\.github\.5thlayer\.libworks\.module' version '\([^']*\)'.*/\1/p" "$dir/build.gradle" 2>/dev/null || true)
    if [ -n "$version" ]; then
        plugin "$version"
        (cd "$dir" && ./gradlew -q libworksUpstreams) > "$dir.upstreams"
        while read -r r t p; do
            [ -n "$r" ] || continue
            upstream "$r" "$t" $p
        done < "$dir.upstreams"
    fi
    cd "$dir" && ./gradlew publishToMavenLocal
)

while read -r repo tag project; do
    [ -n "$repo" ] || continue
    upstream "$repo" "$tag" $project
done
