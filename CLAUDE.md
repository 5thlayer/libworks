# Example Module

A 5thlayer Module: a mod its Consumers, other Modules or a pack, read from the local maven repository by version. See `GLOSSARY.md` for the domain glossary.

<!-- template-only: scripts/fill-template.sh removes this section -->
## This checkout is the template

libworks is the template every 5thlayer Module starts from (FactoryWorks ADR-0090), not a Module itself. `Example Module` and `examplelib` are placeholders; leave them. A change here lands in future Modules only; existing ones pick it up by hand. Work comes from drift across Modules (#1), so an empty tracker is normal.

The build applies the libworks convention plugin (ADR 0003), built here from `plugin/` as an included build, beside `runtime/`; `fill-template.sh` removes both, along with `scripts/` and `module-ci.yml`, and pins a Module to the released plugin. `sh ./gradlew build` runs the plugin's tests too, and `python3 -m unittest discover scripts/tests` tests its bundled upload step against a stand-in server on localhost, CI's upstream step and `fill-template.sh`; `ci.yml` runs them beside `module-ci.yml`. The plugin and the runtime release apart from the template: `docs/plugin.md` and `docs/runtime.md`.
<!-- /template-only -->

## Workflow

Commit on the current branch; open a feature branch only when the user asks for one. Nothing is pushed without the user's word. Asking a session to steward a PR is the user's word for that PR's pushes, its rebases onto `main`, and its merge, under the `steward` skill.

Anything that changes the Module's behaviour gets a `/code-review`: the project skill in `.claude/skills/code-review`, from mattpocock/skills, never the built-in review of the same name. Doc and plumbing changes skip it: that covers `CLAUDE.md`, `GLOSSARY.md`, ADRs, `docs/`, `.claude/`, submodule bumps, and tooling or CI config.

## Commits

Conventional commits: `<type>(<optional scope>): <summary>`, with the summary in the imperative and lower case. The types in use are `feat`, `fix`, `refactor`, `test`, `docs`, `build`, `ci` and `chore`. A breaking change marks its type with `!` (`feat!: ...`), and its release bumps the minor (ADR 0001). A commit that closes an issue ends its body with `Closes #<n>`.

## Testing

`sh ./gradlew build` runs the JUnit tests, on a plain JVM with no Minecraft. `sh ./gradlew runGameTestServer` runs the game tests headless, a real player on a real server, and names each one it ran; it fails if it ran none. The `skillworks:gametest-loop` skill runs a subset, `--tests 'examplelib:<glob>' 1`, one glob a run, so a ticket's new game tests share a name prefix that selects them all. A new game test class is registered by a line in `ExampleLibGameTests.registerTests`, and its tests stand on the `gametest/platform` structure that the plugin's `buildGametestStructures` task writes. Data only the Module's own runs load lives in `src/gametest/resources`, which never ships (libworks ADR 0004): `DevPacks` switches on `gametest_pack/` in the game tests and `dev_pack/` in the dev client, the datapacks that stand in for what a Consumer fills. Packs a test switches on itself, such as one per batch, go under `gametest_packs/<name>/`. CI (`.github/workflows/ci.yml`) runs both through libworks' `module-ci.yml` on every push and never publishes.

Vanilla's and NeoForge's own code is in `build/moddev/artifacts/minecraft-patched-*-sources.jar`, read with `unzip -p <jar> net/minecraft/.../X.java`. Check a claim about vanilla behaviour there before stating it.

The `skillworks:quicklaunch` skill opens the dev client into the most recent save in `run/saves`, one client per checkout.

## Releases

A change a Consumer can use or will notice adds its line under `## Unreleased` in `CHANGELOG.md` as it lands. Before bumping `mod_version`, publishing to `~/.m2`, tagging a release or uploading to Modrinth or CurseForge, read `docs/agents/releases.md`: releases go through `sh ./gradlew release`, which uploads last with the `upload` task when `gradle.properties` names a project, and a published version never changes, in `~/.m2` or on either site. ADR 0001, inherited from 5thlayer/libworks, sets the version bumps.

A release that must reach a Consumer in another checkout, a Module or a pack that pins the jar, follows the `release-train` skill: one owning session per checkout, and pushes only on the user's word.

## Agent skills

### Issue tracker

Issues and specs live in this repo's GitHub Issues, managed with the `gh` CLI. See `docs/agents/issue-tracker.md`.

### Triage labels

The five canonical triage roles, used verbatim as label strings. See `docs/agents/triage-labels.md`.

### Domain docs

Single-context: one `GLOSSARY.md` and `docs/adr/` at the repo root. See `docs/agents/domain.md`.
