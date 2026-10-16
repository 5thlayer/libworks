---
status: accepted
---

# The Module tooling ships as a convention plugin through ~/.m2

About 1,100 lines of build logic, scripts and CI were copied from this template into every Module and
drifted (#8, 5thlayer/factoryworks ADR-0128).

**Decision.** libworks carries a Gradle convention plugin in `plugin/`, versioned and tagged
`plugin-v<version>` apart from the template and the runtime, and published only to `~/.m2`, as the
Modules are. The Python scripts are bundled in the plugin jar, so one version covers
build logic and scripts; their tests stay in libworks. CI is a reusable workflow,
`.github/workflows/module-ci.yml`, called at the same tag. A Module declares its upstream Modules
once in the plugin's extension, each as a version range; that one declaration drives the dependency,
the check of a `-PsiblingBuilds` checkout against the range, the refusal to release on sibling
builds, and the list CI builds from tags first. Tags default to `<artifact>-v<version>`. What can be
derived from the Module's repo is not configured: the jar must carry every file under `LICENSES/`
and `NOTICE` if present, and the plugin generates only the default game-test platform, so a Module
needing other structures commits them. The template stays, shrunk to a build that applies the
plugin.

**Considered.** The Gradle Plugin Portal needs an account and makes every publish public and
permanent, for no gain: CI already builds upstream Modules from their tags into a runner's
`~/.m2`. Fetching the scripts separately would give them a second version to keep in step. Per-mod
settings for game-test structures and upload checks would have served two Modules each.

**Amended 2026-10-16 (#18).** The plugin declares the Module's `~/.m2` repository itself, exclusive
for the `io.github.5thlayer` group, and refuses one the Module declares. Each Module had kept its own
list of modules, in one of two forms, and only `exclusiveContent` stopped other repositories serving
5thlayer coordinates. A filter built from the extension's upstreams would have missed an upstream's
own upstreams and Modules only the dev client loads, so it is by group.
