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
# namespace), the class prefix `ExampleLib` and the display name `Example Library`. gradle.properties
# holds the rest, and the build, the mods.toml and the scripts read it from there. maven_group stays
# io.github.5thlayer, which every Library shares. It commits nothing.
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

package="io/github/_5thlayer"
git mv "src/main/java/$package/examplelib" "src/main/java/$package/$id"
git mv "src/test/java/$package/examplelib" "src/test/java/$package/$id"
git mv "src/main/resources/data/examplelib" "src/main/resources/data/$id"
git mv "src/main/java/$package/$id/ExampleLib.java" "src/main/java/$package/$id/$class.java"
git mv "src/main/java/$package/$id/gametest/ExampleLibGameTests.java" "src/main/java/$package/$id/gametest/${class}GameTests.java"

# Every tracked text file that names a placeholder, but not this script, which is deleted below.
escape() { printf '%s' "$1" | sed -e 's/[\/&|]/\\&/g'; }
git grep -lI -e examplelib -e ExampleLib -e 'Example Library' -- . ':!scripts/fill-template.sh' | while read -r file; do
    sed -i.bak -e "s|examplelib|$id|g" -e "s|ExampleLib|$class|g" -e "s|Example Library|$(escape "$name")|g" "$file"
    rm "$file.bak"
done
if [[ -n "$description" ]]; then
    sed -i.bak "s|^mod_description = .*|mod_description = $(escape "$description")|" gradle.properties
    rm gradle.properties.bak
fi

git rm -q scripts/fill-template.sh
echo "Filled the template as $id ($class, \"$name\"). Next:"
echo "  - write GLOSSARY.md's first terms and README.md's pitch"
echo "  - if the Library goes to Modrinth and CurseForge, set its projects and dependencies in gradle.properties"
echo "  - sh ./gradlew build runGameTestServer"
echo "  - commit: chore: fill the libworks template"
