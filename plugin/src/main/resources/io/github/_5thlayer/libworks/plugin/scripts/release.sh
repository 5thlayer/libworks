#!/usr/bin/env bash
# SPDX-FileCopyrightText: 2026 5thlayer
# SPDX-License-Identifier: MIT
#
# Release the Module at <version> from HEAD: the changelog's Unreleased entries become <version>'s,
# the build and game tests pass, and the jar is published to the local maven repository, tagged,
# and, with --upload, uploaded to Modrinth and CurseForge by upload.py, when gradle.properties names a project.
#
#   sh ./gradlew release -PreleaseVersion=<version> [-Pupload]
#
# The libworks plugin bundles it and runs it in the Module's directory, with $LIBWORKS_TAG_PREFIX
# the Module's tag prefix (libworks { tagPrefix }); the plugin refuses it under -PsiblingBuilds.
#
# It stops after the tag and prints the upload command, since an upload is public and for good and
# waits on the user's word; --upload uploads too. Flags may come in any position.
#
# It commits and tags but pushes nothing to git. The rules it keeps are in docs/agents/releases.md.
# $MAVEN_REPO_LOCAL publishes somewhere other than ~/.m2/repository, to try the script out, and
# then the upload is only a dry run.
set -euo pipefail
here="$(cd "$(dirname "$0")" && pwd)"

fail() { echo "release: $*" >&2; exit 1; }

# The Module's names come from gradle.properties, as the build's do.
property() { sed -n "s/^$1 *= *//p" gradle.properties; }
name="$(property mod_name)"
group="$(property maven_group)"
artifact="$(property archives_name)"
[[ -n "$name" && -n "$group" && -n "$artifact" ]] || fail "gradle.properties must name mod_name, maven_group and archives_name."

usage="usage: sh ./gradlew release -PreleaseVersion=<major.minor.patch> [-Pupload]"
upload_now= version=
for arg in "$@"; do
    case "$arg" in
        --upload) upload_now=1 ;;
        -*) fail "unknown flag $arg; $usage" ;;
        *) [[ -z "$version" ]] || fail "a second version, $arg; $usage"; version="$arg" ;;
    esac
done
[[ "$version" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]] || fail "$usage"
[[ -n "${LIBWORKS_TAG_PREFIX:-}" ]] || fail "LIBWORKS_TAG_PREFIX is unset; run it as sh ./gradlew release."
tag="$LIBWORKS_TAG_PREFIX$version"
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

# Last, so a failed upload leaves the local release and its tag as they are, to retry on their own.
# A trial against another maven repository only shows what it would upload.
upload=(python3 "$here/upload.py")
[[ -z "${MAVEN_REPO_LOCAL:-}" ]] || upload+=(--dry-run)
projects="$(property modrinth_project_id)$(property curseforge_project_id)${MODRINTH_PROJECT_ID:-}${CURSEFORGE_PROJECT_ID:-}"
if [[ -z "$projects" ]]; then
    echo "gradle.properties names no Modrinth or CurseForge project, so nothing is uploaded."
elif [[ -z "$upload_now" ]]; then
    echo "Upload with: sh ./gradlew upload -PreleaseVersion=$version"
elif ! "${upload[@]}" "$version"; then
    echo "release: $version is released and tagged, but an upload failed; retry it with" >&2
    echo "release:   sh ./gradlew upload -PreleaseVersion=$version -Psite=<site>" >&2
    echo "release: for each site named above." >&2
fi
echo "Push with: git push origin HEAD $tag"
