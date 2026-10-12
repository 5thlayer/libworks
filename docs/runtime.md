# The runtime jar

`runtime/` builds `io.github.5thlayer:libworks-runtime`, the code several Modules share (ADR-0128). A Module nests it with jar-in-jar, so players install nothing extra. It is a small mod, id `libworks`, with a `neoforge.mods.toml` and an `@Mod` entry class (`Libworks`), not a plain library: it registers the Works creative tab. FML loads only the newest nested copy, once.

| Class | For |
|---|---|
| `io.github._5thlayer.libworks.WorksTab` | `KEY`, the `ResourceKey<CreativeModeTab>` of `libworks:works`, the one "Works" tab the whole FactoryWorks suite shares. Its icon is vanilla's iron pickaxe, so it exists without any Module. |
| `io.github._5thlayer.libworks.transfer.GuardedResourceHandler` | A transfer face with per-slot rules. NeoForge's `DelegatingResourceHandler` sends slot-less insert and extract past them; this loops through its own slots instead. |
| `io.github._5thlayer.libworks.ghost.Ghost` | `Ghost(ItemStack stack, int x, int y)`: a faded item and where it is drawn, in screen coordinates. |
| `io.github._5thlayer.libworks.ghost.Ghosts` | A screen's ghosts, the items an empty slot would take: positions registered once, each with its candidates, which cycle one a second. No client types, so a game test reads it. |
| `io.github._5thlayer.libworks.ghost.client.GhostDraw` | `draw(graphics, font, ghost)`, the one look for the whole suite. Client-only. |

## Using it from a Module

```groovy
repositories { mavenLocal() }

dependencies {
    implementation 'io.github.5thlayer:libworks-runtime:[0.1.1,0.2)'
    jarJar(implementation('io.github.5thlayer:libworks-runtime')) {
        version { strictly '[0.1.1,0.2)'; prefer '0.1.1' }
    }
}
```

The range is what makes two Modules nesting different patches load one copy: FML keeps the highest version in range. A breaking change bumps the minor, which leaves the range, as ADR 0001 sets for every version here.

A modpack pins the Module's jar, which carries the nested jar; nothing else is installed.

## The Works tab

Every Module shows its items in `libworks:works`; libworks registers it, a Module only adds to it:

```java
modBus.addListener((BuildCreativeModeTabContentsEvent event) -> {
    if (event.getTabKey() == WorksTab.KEY) {
        event.accept(MyItems.GEAR);
        event.accept(MyItems.BELT);
    }
});
```

A Module adds its items in one block, in the order it wants them. The order between Modules follows the mod load order, which a Module does not control and must not depend on. A Module that needs its items elsewhere as well adds them to a vanilla tab in the same listener.

## Ghosts

A ghost is a faded item in an empty slot that shows what goes there. A screen owns one `Ghosts`, registers its positions once, and draws what `shown` returns:

```java
private final Ghosts ghosts = new Ghosts(() -> leftPos, () -> topPos);

// in init(), or the constructor once the menu's slots exist
ghosts.add(menu.slots.get(FUEL_SLOT));                                   // every item the slot's mayPlace takes
ghosts.add(menu.slots.get(INPUT_SLOT), () -> Ghosts.of(sizedIngredient)); // an ingredient's members, at its count
ghosts.add(HEAD_X, HEAD_Y, () -> List.of(product));                      // not a slot: from the panel's corner too

// in a pass drawn in screen coordinates, such as extractBackground
for (Ghost ghost : ghosts.shown(Util.getMillis())) {
    GhostDraw.draw(graphics, font, ghost);
}

// in EMI's or JEI's glue
ghosts.at(mouseX, mouseY, Util.getMillis()).map(Ghost::stack);
```

A ghost stands at the slot's `x` and `y`, or the `x` and `y` given, plus the suppliers' corner, read each time, so it follows a screen that moves on resize; a `new Ghosts()` has the corner at 0, so its positions are screen coordinates. `Ghost`s are in screen coordinates, so `GhostDraw.draw` goes in a pass drawn in them (`extractBackground`, or after `super.render`), not in `extractLabels`, which is translated to the panel's corner. A slot that holds an item, or is inactive, shows no ghost. A position with several candidates shows one for `Ghosts.CYCLE_MILLIS` (1000) and then the next, picked by the `millis` passed in; `Ghosts` never reads the clock. `at` takes the mouse as `double`, which an `int` caller passes as is.

`add(Slot)` scans the item registry once, on first use, and keeps the result. Any other candidates that cost a scan go through `Ghosts.cached(...)`. What a slot takes is the Module's to decide, and EMI and JEI glue stays in the Module: the runtime depends on neither. Jade is the one compat mod the runtime builds against, and only a Module's own plugin loads it (ADR 0005).

## Language

The runtime's own terms. They are not in `GLOSSARY.md`, which `fill-template.sh` copies into every new Library.

**Machine screen**:
A Module's screen for one of its machines, drawn from fills in the suite's one stand-in look until it has art: a grey panel, its slots, bars and gauges, and a status line.
_Avoid_: GUI, menu (the menu is the server half)

**Recess**:
The bevel a slot, bar or gauge sits in: dark above and left, light below and right, grey inside.
_Avoid_: slot background, inset

**Bar**:
A filled strip in its recess showing how far along something is, such as fuel burnt, energy stored or a craft's progress, with its label over it when the label fits.
_Avoid_: progress bar (only one kind of bar)

**Gauge**:
A bar that shows a tank: the fluid's own texture, tinted as in the world, filling the part of the gauge the fluid fills.
_Avoid_: fluid bar, tank bar

**Status line**:
What a machine says it is doing, in a sentence, on its screen and in Jade.
_Avoid_: state text

## Releasing it

The runtime has its own version, `runtime_version` in `runtime/gradle.properties`, apart from the template's `mod_version`, and its own release path: the template's `release` task does not release it, nor the plugin (`docs/plugin.md`), and `release-train` does not cover libworks. From a clean main:

1. set `runtime_version`, the bump ADR 0001 sets, and commit `chore(runtime): release <version>`
2. `sh ./gradlew build runGameTestServer`
3. `sh ./gradlew :runtime:publishToMavenLocal`, which refuses a version already in `~/.m2`: a published version never changes, so a fix is the next patch
4. tag `runtime-v<version>`, annotated with the jar's sha256, as the Libraries' `v<version>` tags are: `git tag -a runtime-v<version> -m "libworks-runtime <version>" -m "sha256 $(sha256sum ~/.m2/repository/io/github/5thlayer/libworks-runtime/<version>/libworks-runtime-<version>.jar | cut -d' ' -f1)"`

Nothing is pushed or uploaded without the user's word. `git tag -l 'runtime-v*' -n9` lists the releases.

The runtime's own game test (`sh ./gradlew :runtime:runGameTestServer`, also run by the root's `runGameTestServer`) lives in `runtime/src/gametest`, outside the jar, and checks the tab is registered.

`fill-template.sh` deletes `runtime/` from a Library: it is libworks' own.
