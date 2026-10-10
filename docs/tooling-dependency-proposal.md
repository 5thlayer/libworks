# Proposal: the tooling as a dependency (#8)

Not implemented. It turns on decisions the issue leaves open.

## What is copied today

`scripts/release.sh`, `upload.py` (with `tests/test_upload.py` and `standin.py`), `build-gametest-structures.py`, `.github/workflows/ci.yml`, and the Groundworks range check in `build.gradle`, about 1,100 lines.

## Sketch

- A convention plugin `io.github.5thlayer.module`, applying `java-library`, `maven-publish` and ModDevGradle, and carrying today's `build.gradle`: Java 25 toolchain, game test run and report check, licensing check, `processResources` expansion, the immutable `publishToMavenLocal`.
- The scripts ship as plugin resources and are extracted to `build/libworks/`; tasks `release`, `upload` and `buildGametestStructures` run them. A Module has no `scripts/` of its own.
- CI is a reusable workflow, `5thlayer/libworks/.github/workflows/module-ci.yml@<tag>`, which a Module calls in a few lines.
- Per-mod differences become an extension: `libworks { tagPrefix = 'v'; groundworks = '[0.3,0.4)'; extraStructures = ... }`, with `gradle.properties` keys kept for the ids and versions already there.

## Questions

1. Delivery: `~/.m2` through `pluginManagement { repositories { mavenLocal() } }`, as Modules consume Libraries, or the Gradle plugin portal? Local keeps one channel and needs no account, but CI runners cannot see another machine's `~/.m2`; the reusable workflow would have to publish libworks first or check it out.
2. How are the Python scripts versioned against the plugin: bundled in the plugin jar (one version) or fetched separately? Bundled is simpler.
3. Beltworks' `upload.py` and Beltworks' and Wireworks' `build-gametest-structures.py` diverged: which behaviour is right, and does it become an option or the default? This needs a diff reviewed with the owner.
4. Is the reusable CI workflow addressed by tag, and does the workflow's checkout need a token for a private libworks?
5. Migration order: the issue's done condition wants one Module first. Craftworks is closest to the template (its `ci.yml` differs only in its Libraries step); Beltworks last, being most diverged.
6. Does the template stay (for `fill-template.sh`) once Modules depend on the plugin, shrunk to a build that applies it?
