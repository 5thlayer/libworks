# libworks

The GitHub template every 5thlayer Library starts from (FactoryWorks ADR-0090). A Library is a 5thlayer mod the FactoryWorks Pack consumes as a pinned local jar.

libworks holds no runtime code, and no Library depends on it. What it holds is copied: a NeoForge mod named `examplelib` that builds, passes its own tests, and does nothing else.

## What a Library gets

- A ModDevGradle build on Java 25 and NeoForge 26.1.2, published to `~/.m2` only.
- A GameTest harness: `runGameTestServer` runs the Library's game tests headless and fails if it ran none. `scripts/build-gametest-structures.py` writes the stone platform they stand on.
- JUnit tests on a plain JVM, with no Minecraft.
- `scripts/release.sh`, which releases a version to `~/.m2` and tags it; a published version never changes. The `skillworks:quicklaunch` skill opens the dev client into the most recent save in `run/saves`, one client per checkout.
- CI on every push: the build, the JUnit tests, the game tests, and a REUSE lint. It never publishes.
- MIT under REUSE, `CLAUDE.md`, a `GLOSSARY.md` stub, `docs/agents/`, conventional commits.
- The engineering skills from [mattpocock/skills](https://github.com/mattpocock/skills), a submodule at `vendor/mattpocock-skills` pinned to a release and linked into `.claude/skills/`.
- ADR 0001, the versioning rule every Library inherits: below 1.0 an addition bumps the patch.

## Starting a Library

1. Create the repo from this template: `gh repo create 5thlayer/<mod_id> --template 5thlayer/libworks --public --clone`, then `git submodule update --init` so the links in `.claude/skills/` resolve.
2. Fill the placeholders: `scripts/fill-template.sh <mod_id> <ClassName> "<Display Name>" "<description>"`. It renames the package and every `examplelib`, `ExampleLib` and `Example Library`, and deletes itself.
3. Replace this README with the Library's own, write `GLOSSARY.md`'s first terms, and run `sh ./gradlew build runGameTestServer`.

## Adopting it in an existing Library

A Library that predates the template is not regenerated. Diff it against the template and take the template's version of each shared piece: `build.gradle`, the game test harness (`CodeGameTest` and the `*GameTests` registrar), the scripts, `REUSE.toml`, `docs/agents/`, `.github/workflows/`, and the skills submodule with its links. Its own versioning ADR becomes a pointer to libworks' ADR 0001.

## Changing the template

A change here reaches no Library by itself: each adopts it by diff. If one tool keeps drifting between Libraries, it is promoted to a build-time Gradle artifact published from here, never a runtime jar.
