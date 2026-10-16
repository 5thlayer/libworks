# libworks

The GitHub template every 5thlayer Module starts from (FactoryWorks ADR-0090). A Module is a 5thlayer mod its Consumers, other Modules or a pack, read from the local maven repository by version.

libworks is becoming a dependency (ADR-0128, #8). It carries `runtime/`, a small mod (id `libworks`) Modules nest: the shared "Works" creative tab and transfer helpers (`docs/runtime.md`); and `plugin/`, the Gradle convention plugin every Module applies, with the scripts and the CI it calls (`docs/plugin.md`, ADR 0003). The template below is a NeoForge mod named `examplelib` whose build applies that plugin, passes its own tests, and does nothing else.

## What a Module gets

- A build that applies the libworks plugin at a released version: ModDevGradle on Java 25 and NeoForge 26.1.2, published to `~/.m2` only.
- A GameTest harness: `runGameTestServer` runs the Module's game tests headless and fails if it ran none. `buildGametestStructures` writes the stone platform they stand on.
- JUnit tests on a plain JVM, with no Minecraft.
- `sh ./gradlew release -PreleaseVersion=<version>`, which releases a version to `~/.m2` and tags it; a published version never changes. The `skillworks:quicklaunch` skill opens the dev client into the most recent save in `run/saves`, one client per checkout.
- CI on every push, through libworks' `module-ci.yml` at the plugin's tag: the build, the JUnit tests, the game tests, a REUSE lint, and `scripts/build-textures.py --check` when the Module has that script. It never publishes.
- MIT under REUSE, with a `NOTICE` for the credits of third-party material and a commented `REUSE.toml` stanza for its licence, `CLAUDE.md`, a `GLOSSARY.md` stub, `docs/agents/`, conventional commits.
- The engineering skills from [mattpocock/skills](https://github.com/mattpocock/skills), a submodule at `vendor/mattpocock-skills` pinned to a release and linked into `.claude/skills/`.
- ADR 0001, the versioning rule every Module inherits: below 1.0 an addition bumps the patch.

## Starting a Module

1. Create the repo from this template: `gh repo create 5thlayer/<mod_id> --template 5thlayer/libworks --public --clone`, then `git submodule update --init` so the links in `.claude/skills/` resolve.
2. Fill the placeholders: `scripts/fill-template.sh <mod_id> <ClassName> "<Display Name>" "<description>"`. It renames the package and every `examplelib`, `ExampleLib` and `Example Module`, removes `plugin/`, `runtime/` and the tooling's tests, pins the plugin and `module-ci.yml` to the released plugin version, and deletes itself. The plugin must be in `~/.m2` (`docs/plugin.md`).
3. Write the README's technical sections below the placeholder `fill-template.sh` leaves for the maintainer's section for players, which is the maintainer's to write and the marketplace description (`docs/agents/publishing.md`). Write `GLOSSARY.md`'s first terms, and run `sh ./gradlew build runGameTestServer`.

## Adopting it in an existing Module

A Module that predates the template is not regenerated. Diff it against the template and take the template's version of each shared piece: `settings.gradle` and `build.gradle` applying the plugin in place of its own build logic and `scripts/`, the game test harness (`CodeGameTest` and the `*GameTests` registrar), `REUSE.toml` and `NOTICE`'s preamble, `docs/agents/`, `.github/workflows/`, and the skills submodule with its links. Its own versioning ADR becomes a pointer to libworks' ADR 0001.

## Changing the template

A change here reaches no Module by itself: each adopts it by diff. Build logic, scripts and CI live in the plugin instead, so a Module takes a change there by moving its plugin version.
