---
status: accepted
---

# A Module's game-test code never ships

Every Module kept its game-test code in `src/main`: the `*GameTests` classes, their test blocks, items
and environments, and `structure/gametest/platform.nbt`. All of it reached players, and each Module
kept it from running with a switch of its own, or none: game tests are enabled in every dev run,
upstream jars included, so one Module's test blocks registered in another's dev client (#17).

**Decision.** A Module's game-test code and its test-only resources live in the `gametest` source set
beside its packs (ADR 0004), which only the Module's own runs load. Its `*GameTests` class is an
`@EventBusSubscriber` that takes the mod bus from `FMLConstructModEvent`, so the `@Mod` class names no
game-test code. Nothing on the game-test side is gated: it registers in all of the Module's own runs,
and a Consumer's runs, which load the Module's jar, never see it. `main` never names game-test code.
Where shipped code must report to a test, `main` holds a hook with no test vocabulary that the
game-test side fills. A test-only file moves whole; an entry inside a shipped file cannot, since a
path in both source sets shows only one copy: a data entry goes into a Game-test pack, and a test
block's lang line is dropped. The convention plugin compiles libworks-gametest (ADR 0008) on the
`gametest` source set only, and `buildGametestStructures` writes into `src/gametest/resources`.

A Consumer never runs or reuses a Module's game tests. A check two Modules share goes to
libworks-gametest, and a Consumer that tests against a Module writes its own tests on its API.

**Considered.** Staying in `src/main` behind one shared property, such as `libworks.run`: test code in
what players download, and a switch a modpack could find and lean on, which ADR 0004 rejected for the
packs. Registering through `RegisterEvent` directly, as Craftworks' `KeepsDataRecipe` does: every
Module's `DeferredRegister`s rewritten for no gain. A mixin on the game-test side with its own config:
`neoforge.mods.toml` ships and lists the configs, and one missing from the jar fails in production.
A plugin check for game-test code left in `main`: the compile error from libworks-gametest's move
already names it.

## Consequences

- The plugin change breaks a Module whose `main` still compiles against libworks-gametest, so it
  releases as plugin 0.4.0 (ADR 0001), and every Module moves through `template-drift`, each adding
  a `## Unreleased` line that its game-test code no longer ships.
- ADR 0008's "`compileOnly` and `localRuntime`" becomes the `gametest` source set's `compileOnly` and
  `localRuntime`.
- The `<modid>.gametest` properties, and the `isGametestEnabled()` checks around registration, go.
  `libworks.run` stays, to tell `DevPacks` which pack to switch on.
- Test blocks also register in the Module's own dev client, where `/test` runs its game tests.
