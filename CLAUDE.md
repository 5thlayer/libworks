# Example Library

A 5thlayer Library: a mod the PlanetaryFactory Pack consumes as a pinned local jar. See `CONTEXT.md` for the domain glossary.

<!-- template-only: extract-library removes this section -->
## This checkout is the template

libworks is the template every 5thlayer Library starts from (planetary-factory ADR-0090), not a Library itself. `Example Library` and `examplelib` are placeholders; leave them. A change here lands in future Libraries only; existing ones pick it up by hand. Work comes from drift across Libraries (#1), so an empty tracker is normal.
<!-- /template-only -->

## Workflow

Commit on the current branch; open a feature branch only when the user asks for one. Nothing is pushed without the user's word.

Anything that changes the Library's behaviour gets a `/code-review`. Doc and plumbing changes skip it: that covers `CLAUDE.md`, `CONTEXT.md`, ADRs, `docs/`, `.claude/`, and tooling or CI config.

## Commits

Conventional commits: `<type>(<optional scope>): <summary>`, with the summary in the imperative and lower case. The types in use are `feat`, `fix`, `refactor`, `test`, `docs`, `build`, `ci` and `chore`. A breaking change marks its type with `!` (`feat!: ...`), and its release bumps the minor (ADR 0001). A commit that closes an issue ends its body with `Closes #<n>`.

## Testing

`sh ./gradlew build` runs the JUnit tests, on a plain JVM with no Minecraft. `sh ./gradlew runGameTestServer` runs the game tests headless, a real player on a real server, and names each one it ran; it fails if it ran none. A new game test class is registered by a line in `ExampleLibGameTests.registerTests`, and its tests stand on the `gametest/platform` structure that `scripts/build-gametest-structures.py` writes. CI (`.github/workflows/ci.yml`) runs both on every push and never publishes.

The `skillworks:quicklaunch` skill opens the dev client into the most recent save in `run/saves`, one client per checkout.

## Releases

A change a Consumer can use or will notice adds its line under `## Unreleased` in `CHANGELOG.md` as it lands. Before bumping `mod_version`, publishing to `~/.m2` or tagging a release, read `docs/agents/releases.md`: releases go through `scripts/release.sh`, and a published version never changes. ADR 0001, inherited from 5thlayer/libworks, sets the version bumps.

A release that must reach another Library or the Pack follows the `release-train` skill: one owning session per checkout, and pushes only on the user's word.

## Agent skills

### Issue tracker

Issues and specs live in this repo's GitHub Issues, managed with the `gh` CLI. See `docs/agents/issue-tracker.md`.

### Triage labels

The five canonical triage roles, used verbatim as label strings. See `docs/agents/triage-labels.md`.

### Domain docs

Single-context: one `CONTEXT.md` and `docs/adr/` at the repo root. See `docs/agents/domain.md`.
