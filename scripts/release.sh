#!/usr/bin/env bash
# SPDX-FileCopyrightText: 2026 5thlayer
# SPDX-License-Identifier: MIT
#
# Release the Library at <version> from HEAD: the changelog's Unreleased entries become <version>'s,
# the build and game tests pass, and the jar is published to the local maven repository and tagged.
#
#   scripts/release.sh <version>
#
# It commits and tags but pushes nothing. The rules it keeps are in docs/agents/releases.md.
# $MAVEN_REPO_LOCAL publishes somewhere other than ~/.m2/repository, to try the script out.
set -euo pipefail
cd "$(dirname "$0")/.."

fail() { echo "release: $*" >&2; exit 1; }

# The Library's names come from gradle.properties, as the build's do.
property() { sed -n "s/^$1 *= *//p" gradle.properties; }
name="$(property mod_name)"
group="$(property maven_group)"
artifact="$(property archives_name)"
[[ -n "$name" && -n "$group" && -n "$artifact" ]] || fail "gradle.properties must name mod_name, maven_group and archives_name."

version="${1:-}"
[[ "$version" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]] || fail "usage: scripts/release.sh <major.minor.patch>"
tag="v$version"
repo="${MAVEN_REPO_LOCAL:-$HOME/.m2/repository}"
published="$repo/${group//.//}/$artifact/$version"

[[ -z "$(git status --porcelain)" ]] || fail "the working tree has changes; commit or stash them first."
! git rev-parse -q --verify "refs/tags/$tag" > /dev/null || fail "$tag already exists."
[[ ! -e "$published" ]] || fail "$version is already in $published, and a published version never changes."

# The entries between "## Unreleased" and the next heading are what this release ships.
entries="$(awk '/^## /{on = ($0 == "## Unreleased"); next} on && NF' CHANGELOG.md)"
[[ -n "$entries" ]] || fail "CHANGELOG.md has nothing under \"## Unreleased\"; a release ships what it lists."

# Until the commit, a failure puts both files back, so a failed release leaves nothing behind.
trap 'git checkout -- gradle.properties CHANGELOG.md' EXIT
sed -i.bak "s/^mod_version = .*/mod_version = $version/" gradle.properties && rm gradle.properties.bak
awk -v v="$version" '{print} $0 == "## Unreleased" {print ""; print "## " v}' CHANGELOG.md > CHANGELOG.md.new
mv CHANGELOG.md.new CHANGELOG.md

sh ./gradlew build runGameTestServer
git commit -q -m "chore: release $version" -- gradle.properties CHANGELOG.md
trap - EXIT

sh ./gradlew "-Dmaven.repo.local=$repo" publishToMavenLocal
sha="$(shasum -a 256 "$published/$artifact-$version.jar" | cut -d' ' -f1)"
git tag -a "$tag" -m "$name $version" -m "jar sha256 $sha"

echo "Published $published"
echo "jar sha256 $sha"
echo "Push with: git push origin HEAD $tag"
