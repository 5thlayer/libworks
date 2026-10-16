---
status: accepted
---

# A Module's dev and game-test packs never ship

Six Modules kept the datapacks only their own runs load in five ways: in the jar, inert behind a
property of their own (Craftworks, Fieldworks, Groundworks, Wireworks), in `src/main` left out by a jar
exclude (Voidworks), or in a source set of their own (Pipeworks) (#6).

**Decision.** A Module's game-test packs and dev pack live in a `gametest` source set, under
`src/gametest/resources`, which the dev runs load and the jar leaves out. The convention plugin
(libworks ADR 0003) creates the source set, adds it to the mod, and sets `libworks.run` to each run's
name. The template's `DevPacks`, in `src/gametest/java`, adds `dev_pack/` on the `client` run and
`gametest_pack/` on `gameTestServer`, when the folder exists. Packs a Module switches on itself, such
as one per test batch, go under `gametest_packs/<name>/` and keep their own switch. The property needs
no Module's name: no Consumer's run ever sees another Module's packs.

**Considered.** In the jar, inert behind a property: test data in what players download, and a switch
a modpack could find and lean on. In `src/main` behind a jar exclude: an exclude list to keep in step
with each Module's folders. The pack finder in libworks-runtime: test-only code in every player's
jar, and the runtime required of any Module with a pack. Where the game-test code itself lives,
still in `src/main` and shipped, is #17.

_Since ADR 0011, the game-test code lives in the `gametest` source set too._
