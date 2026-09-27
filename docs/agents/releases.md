# Releases

A release reaches Consumers through the local maven repository (`~/.m2`) at a version of its own. Consumers read the Library from there by version, and the Pack (adamico/planetary-factory) pins the jar's sha256. That only works if the jar published at a version is the one jar that version ever names.

Nothing publishes anywhere else. CI builds and tests on every push but never publishes.

## The changelog

Each change a Consumer can use or will notice adds its line under `## Unreleased` in `CHANGELOG.md` when it lands, written for a Consumer's author in the glossary's terms, with its issue number. A release ships what Unreleased lists, so the changelog is written as the work is done and never reconstructed from commits.

## Cutting a release

`scripts/release.sh <version>` from a clean main. ADR 0001 sets the semver: below 1.0 only a breaking change bumps the minor version, and an addition or a fix is the next patch. The script refuses a version that is already tagged or in `~/.m2`, and an empty Unreleased. It then:

1. sets `mod_version` and turns `## Unreleased` into `## <version>` under a fresh, empty Unreleased
2. runs the build and the game tests, putting both files back if either fails
3. commits `chore: release <version>`, runs `publishToMavenLocal`, and tags `v<version>` with the jar's sha256

It pushes nothing, and ends by printing the push command. To try the script out, set `MAVEN_REPO_LOCAL` to a scratch folder and run it in a throwaway clone: a version published to the real `~/.m2` is permanent.

A release that must reach another Library or the Pack follows the `release-train` skill.

## A published version is final

A version in `~/.m2` never changes. A fix is the next patch version. `publishToMavenLocal` refuses a version that is already there (`build.gradle`), and nothing is deleted or overwritten by hand to get past it.

## Tags

A release is tagged `v<version>`, annotated with its jar's sha256. `git tag -l 'v*' -n9` lists them.
