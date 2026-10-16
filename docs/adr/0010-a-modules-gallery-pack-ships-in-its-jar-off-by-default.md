---
status: accepted
---

# A Module's Gallery pack ships in its jar, off by default

A player who installs a Module has no way to see what it does short of building each feature by hand,
and the Showcase adds no content of its own to show them (FactoryWorks ADR-0131). The one gallery
there was, Fieldworks' two scenes, lived in an ignored dev save, unversioned and free to break unseen
(FactoryWorks #691, #29).

**Decision.** Each Module ships a Gallery pack, `gallery_pack/` in its jar: a datapack of functions
only, no recipes, loot tables, tags or worldgen, so turning it on changes nothing about how the
Module plays. `<modid>:gallery/build` lays out one working scene per README feature, each in a plot of
its own along a strip east of the player, and `<modid>:gallery/<scene>` builds one. Unlike a Dev pack
or a Game-test pack (ADR 0004) it is for players, so it ships; and since a world made for real play
should hold no gallery unless a player chooses it, it is offered in the Data Packs screen and never
selected by default. libworks-runtime's `GalleryPack` registers it under a pack source that carries
the built-in label and is never added automatically. Groundworks, which nests no runtime, registers
its pack with the same few lines of its own.

Every Module's game tests run its gallery through one check, libworks-gametest's `GalleryCheck`
(ADR 0008): the pack is offered and off by default, every gallery function loads, the build runs
every command without failing, and each scene's key block stands in its plot. A broken gallery fails
the release gate, not a player's world.

**Considered.** Vanilla's `PackSource.FEATURE`, the source of the experimental packs, also never added
automatically: world creation lists every such pack as an experiment. Running the build line by line
so a failure names its command: it names a failure, but not one inside a nested scene function, which
a tracer on the command context, as `/debug function` uses, does. The Showcase shipping
the galleries: a pack with no content of its own (FactoryWorks ADR-0131), and a Module's own gallery
reaches a player who installs it alone.

## Consequences

- A scene command must fail by throwing, as vanilla's do; one that reports a failure and returns 0
  passes the check unseen. `fill` throws when it changes nothing, so a scene clears its plot by
  filling it with `structure_void` before `air`.
- The build changes blocks and their contents. A scene may also set up the Module's own records for
  what it places, through the Module's own operator commands, such as Voidworks' Wells; the check
  asserts nothing of them, and the Module's own test checks and clears them. This settles what
  FactoryWorks #691 left open for a scene that needs more than blocks. The build never changes time,
  weather, game rules or players.
- The check turns the pack on, which reloads the server's data, so a Module runs it in a game-test
  batch of its own.
- The gallery is built beside the test's structure, not in it, so the structure does not bound it. The
  check force-loads and clears the plots, at most `GalleryCheck.MAX_SCENES` of them, a bound on the
  area it loads, raised as a gallery outgrows it.
