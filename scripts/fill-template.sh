#!/usr/bin/env bash
# SPDX-FileCopyrightText: 2026 5thlayer
# SPDX-License-Identifier: MIT
#
# Fill the libworks template's placeholders in a repo just made from it, then delete this script.
#
#   scripts/fill-template.sh <mod_id> <ClassName> "<Display Name>" ["<one-line description>"]
#   scripts/fill-template.sh craftworks Craftworks "Craftworks" "The Personal Assembler."
#
# The placeholders are the mod id `examplelib` (also the package, the artifact and the resource
# namespace), the class prefix `ExampleLib` and the display name `Example Module`. gradle.properties
# holds the rest, and the build, the mods.toml and the scripts read it from there. maven_group stays
# io.github.5thlayer, which every Module shares. It commits nothing.
set -euo pipefail
cd "$(dirname "$0")/.."

fail() { echo "fill-template: $*" >&2; exit 1; }

id="${1:-}"
class="${2:-}"
name="${3:-}"
description="${4:-}"
[[ "$id" =~ ^[a-z][a-z0-9_]*$ ]] || fail "usage: scripts/fill-template.sh <mod_id> <ClassName> \"<Display Name>\" [\"<description>\"]; the mod id is lower case, as a Java package and a namespace both accept."
[[ "$class" =~ ^[A-Z][A-Za-z0-9]*$ ]] || fail "the class name is a Java identifier starting upper case: $class"
[[ -n "$name" ]] || fail "the display name is missing."
[[ -e src/main/java/io/github/_5thlayer/examplelib ]] || fail "no examplelib package here: the template is already filled."

# The runtime jar, the convention plugin and the tooling's own tests and CI belong to libworks, not
# to a Module (ADR-0128, ADR 0003). The Module applies the plugin at the version plugin/ holds, from
# ~/.m2, and calls module-ci.yml at that version's tag.
plugin_version="$(sed -n 's/^plugin_version *= *//p' plugin/gradle.properties)"
[[ -n "$plugin_version" ]] || fail "plugin/gradle.properties names no plugin_version."
git rm -rq runtime plugin scripts/tests .github/scripts .github/workflows/module-ci.yml
# Their docs and libworks' own ADRs go with them: a Module keeps ADR 0001, the versioning rule every
# Module inherits, and numbers its own from 0002.
git rm -q docs/plugin.md docs/runtime.md $(git ls-files 'docs/adr/*.md' | grep -v '^docs/adr/0001-')
sed -i.bak -e '/^\/\/ The runtime jar/d' -e "/^include 'runtime'/d" \
    -e '/The convention plugin a Module applies/,/fill-template.sh swaps/d' \
    -e "/^    includeBuild 'plugin'/d" \
    -e "s|^\\( *\\)maven { url = 'https://maven.neoforged.net/releases' }\$|&\\n\\1mavenLocal()|" settings.gradle
sed -i.bak -e "s|^    id 'io.github.5thlayer.libworks.module'\$|& version '$plugin_version'|" \
    -e '/^\/\/ The template builds the plugin/d' -e "/^\/\/ The template's own checks/,\$d" build.gradle
# REUSE.toml's paths for the scripts and the plugin's resources name nothing in a Module.
sed -i.bak -e '/^    "scripts\//d' -e '/^    "plugin\//d' REUSE.toml
rm settings.gradle.bak build.gradle.bak REUSE.toml.bak
cat > .github/workflows/ci.yml <<YAML
# Builds and tests every push through the CI every Module shares, at the tag of the plugin
# build.gradle applies: the two versions move together (libworks ADR 0003). It never publishes:
# a Module's jar reaches a Consumer only through ~/.m2, and Modrinth and CurseForge, only from a
# maintainer's machine (docs/agents/releases.md).
name: CI

on:
  push:
  pull_request:
  workflow_dispatch:

permissions:
  contents: read

jobs:
  ci:
    uses: 5thlayer/libworks/.github/workflows/module-ci.yml@plugin-v$plugin_version
YAML

package="io/github/_5thlayer"
git mv "src/main/java/$package/examplelib" "src/main/java/$package/$id"
git mv "src/test/java/$package/examplelib" "src/test/java/$package/$id"
git mv "src/gametest/java/$package/examplelib" "src/gametest/java/$package/$id"
git mv "src/gametest/resources/gametest_pack/data/examplelib" "src/gametest/resources/gametest_pack/data/$id"
git mv "src/gametest/resources/data/examplelib" "src/gametest/resources/data/$id"
git mv "src/main/java/$package/$id/ExampleLib.java" "src/main/java/$package/$id/$class.java"
git mv "src/gametest/java/$package/$id/gametest/ExampleLibGameTests.java" "src/gametest/java/$package/$id/gametest/${class}GameTests.java"

# Every tracked text file that names a placeholder, but not this script, which is deleted below.
escape() { printf '%s' "$1" | sed -e 's/[\/&|]/\\&/g'; }
git grep -lI -e examplelib -e ExampleLib -e 'Example Module' -- . ':!scripts/fill-template.sh' | while read -r file; do
    sed -i.bak -e "s|examplelib|$id|g" -e "s|ExampleLib|$class|g" -e "s|Example Module|$(escape "$name")|g" "$file"
    rm "$file.bak"
done
if [[ -n "$description" ]]; then
    sed -i.bak "s|^mod_description = .*|mod_description = $(escape "$description")|" gradle.properties
    rm gradle.properties.bak
fi

# The changelog's entries and the README are libworks' own: the Module starts with an empty
# Unreleased section and a README stub of its name, the place for the maintainer's section for
# players (5thlayer/factoryworks#672), and its description.
sed -i.bak '/^## Unreleased$/q' CHANGELOG.md
rm CHANGELOG.md.bak
{
    printf '# %s\n\n' "$name"
    printf '<!-- For players, written by the maintainer and never by an agent: what %s is, why it exists\n' "$name"
    printf 'and how it feels to play. It is also the Modrinth and CurseForge description\n'
    printf '(docs/agents/publishing.md). The technical sections go below it. -->\n'
    [[ -z "$description" ]] || printf '\n%s\n' "$description"
} > README.md

# CLAUDE.md's template-only section tells an agent the checkout is the template, which a Module is not.
perl -0pi -e 's/\n<!-- template-only:.*?<!-- \/template-only -->\n//s' CLAUDE.md
! grep -q template-only CLAUDE.md || fail "CLAUDE.md still has its template-only section."

git rm -q scripts/fill-template.sh
echo "Filled the template as $id ($class, \"$name\"). Next:"
echo "  - write GLOSSARY.md's first terms, and README.md's technical sections below the maintainer's section for players"
echo "  - if the Module goes to Modrinth and CurseForge, set its projects and dependencies in gradle.properties"
echo "  - sh ./gradlew build runGameTestServer"
echo "  - commit: chore: fill the libworks template"
