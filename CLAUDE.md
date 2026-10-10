# Example Library

A 5thlayer Library: a mod the FactoryWorks Pack consumes as a pinned local jar. See `GLOSSARY.md` for the domain glossary.

<!-- template-only: scripts/fill-template.sh removes this section -->
## This checkout is the template

libworks is the template every 5thlayer Library starts from (FactoryWorks ADR-0090), not a Library itself. `Example Library` and `examplelib` are placeholders; leave them. A change here lands in future Libraries only; existing ones pick it up by hand. Work comes from drift across Libraries (#1), so an empty tracker is normal.
<!-- /template-only -->

## Workflow

Commit on the current branch; open a feature branch only when the user asks for one. Nothing is pushed without the user's word.

Anything that changes the Library's behaviour gets a `/code-review`: the project skill in `.claude/skills/code-review`, from mattpocock/skills, never the built-in review of the same name. Doc and plumbing changes skip it: that covers `CLAUDE.md`, `GLOSSARY.md`, ADRs, `docs/`, `.claude/`, submodule bumps, and tooling or CI config.

## Commits

Conventional commits: `<type>(<optional scope>): <summary>`, with the summary in the imperative and lower case. The types in use are `feat`, `fix`, `refactor`, `test`, `docs`, `build`, `ci` and `chore`. A breaking change marks its type with `!` (`feat!: ...`), and its release bumps the minor (ADR 0001). A commit that closes an issue ends its body with `Closes #<n>`.

## Testing

`sh ./gradlew build` runs the JUnit tests, on a plain JVM with no Minecraft. `sh ./gradlew runGameTestServer` runs the game tests headless, a real player on a real server, and names each one it ran; it fails if it ran none. `python3 -m unittest discover scripts/tests` tests the upload step against a stand-in server on localhost. A new game test class is registered by a line in `ExampleLibGameTests.registerTests`, and its tests stand on the `gametest/platform` structure that `scripts/build-gametest-structures.py` writes. CI (`.github/workflows/ci.yml`) runs all three on every push and never publishes.

The `skillworks:quicklaunch` skill opens the dev client into the most recent save in `run/saves`, one client per checkout.

## Releases

A change a Consumer can use or will notice adds its line under `## Unreleased` in `CHANGELOG.md` as it lands. Before bumping `mod_version`, publishing to `~/.m2`, tagging a release or uploading to Modrinth or CurseForge, read `docs/agents/releases.md`: releases go through `scripts/release.sh`, which uploads last with `scripts/upload.py` when `gradle.properties` names a project, and a published version never changes, in `~/.m2` or on either site. ADR 0001, inherited from 5thlayer/libworks, sets the version bumps.

A release that must reach another Library or the Pack follows the `release-train` skill: one owning session per checkout, and pushes only on the user's word.

## Agent skills

### Issue tracker

Issues and specs live in this repo's GitHub Issues, managed with the `gh` CLI. See `docs/agents/issue-tracker.md`.

### Triage labels

The five canonical triage roles, used verbatim as label strings. See `docs/agents/triage-labels.md`.

### Domain docs

Single-context: one `GLOSSARY.md` and `docs/adr/` at the repo root. See `docs/agents/domain.md`.
