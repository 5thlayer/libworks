# The runtime jar

`runtime/` builds `io.github.5thlayer:libworks-runtime`, the code several Modules share (ADR-0128). A Module nests it with jar-in-jar, so players install nothing extra. It is a small mod, id `libworks`, with a `neoforge.mods.toml` and an `@Mod` entry class (`Libworks`), not a plain library: it registers the Works creative tab. FML loads only the newest nested copy, once.

| Class | For |
|---|---|
| `io.github._5thlayer.libworks.WorksTab` | `KEY`, the `ResourceKey<CreativeModeTab>` of `libworks:works`, the one "Works" tab the whole FactoryWorks suite shares. Its icon is vanilla's iron pickaxe, so it exists without any Module. |
| `io.github._5thlayer.libworks.transfer.GuardedResourceHandler` | A transfer face with per-slot rules. NeoForge's `DelegatingResourceHandler` sends slot-less insert and extract past them; this loops through its own slots instead. |
| `io.github._5thlayer.libworks.ghost.Ghost` | `Ghost(ItemStack stack, int x, int y)`: a faded item and where it is drawn, in screen coordinates. |
| `io.github._5thlayer.libworks.ghost.Ghosts` | A screen's ghosts, the items an empty slot would take: positions registered once, each with its candidates, which cycle one a second. No client types, so a game test reads it. |
| `io.github._5thlayer.libworks.ghost.client.GhostDraw` | `draw(graphics, font, ghost)`, the one look for the whole suite, and `tooltip(...)`, a ghost's item tooltip. Client-only. |
| `io.github._5thlayer.libworks.machine.client.MachineDraw` | A machine screen's one stand-in look: the panel, recesses, bars, gauges, the fluid tooltip, and text cut to fit. Client-only. |
| `io.github._5thlayer.libworks.machine.Tank` | `Tank(id, fluid, capacity, titleKey)`: one of a machine's tanks as Jade shows it. No Jade or client types. |
| `io.github._5thlayer.libworks.machine.jade.MachineReport` | A machine's lines in Jade, such as its status line, from data the server sends. Its `Client` draws them. Jade `compileOnly` (ADR 0005). |
| `io.github._5thlayer.libworks.machine.jade.MachineItems` | A machine's items in Jade, slot by slot, as icons with counts through its `Client`. |
| `io.github._5thlayer.libworks.machine.jade.MachineTanks` | A machine's tanks in Jade, one group each, titled through its `Client`. |

## Using it from a Module

```groovy
repositories { mavenLocal() }

dependencies {
    implementation 'io.github.5thlayer:libworks-runtime:[0.1.2,0.2)'
    jarJar(implementation('io.github.5thlayer:libworks-runtime')) {
        version { strictly '[0.1.2,0.2)'; prefer '0.1.2' }
    }
}
```

A Module that uses `libworks.machine` needs 0.1.2 or later, so its range starts there. The range is what makes two Modules nesting different patches load one copy: FML keeps the highest version in range. A breaking change bumps the minor, which leaves the range, as ADR 0001 sets for every version here.

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

## Machine screens

A machine screen draws its stand-in look with `MachineDraw`, all in screen coordinates, so in `extractBackground` or after `super.render`:

```java
@Override
public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
    super.extractBackground(graphics, mouseX, mouseY, partialTick); // dims the world behind
    MachineDraw.panel(graphics, font, leftPos, topPos, imageWidth, imageHeight, menu.slots, ghosts, Util.getMillis());
    MachineDraw.bar(graphics, font, leftPos + 28, topPos + 20, 80, MachineDraw.BAR_HEIGHT, burnt, BURN, burnLabel);
    MachineDraw.gauge(graphics, font, leftPos + 28, topPos + 38, 80, menu.water(), menu.waterCapacity());
}

@Override
protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
    super.extractLabels(graphics, mouseX, mouseY);
    graphics.text(font, MachineDraw.fitted(font, status, imageWidth - 2 * MachineDraw.MARGIN), MachineDraw.MARGIN, STATUS_Y, MachineDraw.TEXT, false);
}

@Override
protected void extractTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
    super.extractTooltip(graphics, mouseX, mouseY);
    Optional<Ghost> ghost = ghosts.at(mouseX, mouseY, Util.getMillis());
    if (ghost.isPresent()) {
        GhostDraw.tooltip(graphics, font, ghost.get(), mouseX, mouseY);
    } else if (MachineDraw.over(leftPos + 28, topPos + 38, 80, MachineDraw.BAR_HEIGHT, mouseX, mouseY)) {
        MachineDraw.fluidTooltip(graphics, font, menu.water(), menu.waterCapacity(), mouseX, mouseY);
    }
}
```

`panel` recesses only active slots and draws the ghosts over them at the `millis` passed in, as `Ghosts` never reads the clock; the screen keeps its own `Ghosts`. A bar's `fraction` is clamped to 0–1, and its label, white with a shadow, is drawn centred when it fits the width less 4 px and the bar is at least `BAR_HEIGHT` (12) tall; an empty `Component` draws none. A gauge is always `BAR_HEIGHT` tall, tiles the fluid's still texture tinted as in the world over the filled part, and writes the amount in mB when it fits; an empty stack draws only the recess. The runtime owns only the fluid tooltip's two strings, `libworks.machine.fluid_empty` and `libworks.machine.fluid`; bar labels come from the Module. `wholeOnHover(graphics, font, line, width, mouseX, mouseY)` shows a line `fitted` cut, whole.

## Machines in Jade

The runtime builds against Jade but registers nothing (ADR 0005). A Module's own `@WailaPlugin` builds the providers, with uids in its own namespace, and registers them; the runtime's game-test server runs without Jade, which keeps the runtime from loading a Jade class itself.

```java
@WailaPlugin
public class BoilerJadePlugin implements IWailaPlugin {

    private static final Identifier ITEMS = id("boiler_items");
    private static final Identifier TANKS = id("boiler_tanks");
    private static final MachineReport<BoilerBlockEntity, BoilerStatus> REPORT =
            new MachineReport<>(id("boiler"), BoilerBlockEntity.class, BoilerBlockEntity::status, BoilerStatus.STREAM_CODEC);

    @Override
    public void register(IWailaCommonRegistration registration) {
        registration.registerBlockDataProvider(REPORT, BoilerBlockEntity.class);
        registration.registerItemStorage(new MachineItems(ITEMS), BoilerBlockEntity.class);
        registration.registerFluidStorage(new MachineTanks<>(TANKS, BoilerBlockEntity.class, boiler -> List.of(
                new Tank("water", boiler.water(), BoilerSpec.WATER, "wireworks.boiler.tank.water"),
                new Tank("steam", boiler.steam(), BoilerSpec.STEAM, "wireworks.boiler.tank.steam"))), BoilerBlockEntity.class);
    }

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        registration.registerBlockComponent(new MachineReport.Client<>(REPORT, (status, tooltip) -> tooltip.add(status.line())), BoilerBlock.class);
        registration.registerItemStorageClient(new MachineItems.Client(ITEMS));
        registration.registerFluidStorageClient(new MachineTanks.Client(TANKS));
    }
}
```

Each provider's server half names no client type and none of Jade's drawing types, so a dedicated server with Jade never loads them; a `Client` takes its server half's uid. `MachineReport` sends nothing for a block entity of another type. `MachineItems` lists the item capability's non-empty slots. `MachineTanks` gives each `Tank` its own group, where Jade's own reading of the fluid capability would merge them, titled from the tank's `titleKey`, or Jade's untitled view when it is `null`. The Module's mods.toml lists Jade as optional, if at all; the runtime's does not name it.

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

The runtime's own game tests (`sh ./gradlew :runtime:runGameTestServer`, also run by the root's `runGameTestServer`) live in `runtime/src/gametest`, outside the jar: the tab is registered, ghosts, and a `Tank` built on a server with no Jade (ADR 0005). Its JUnit tests run under FML (ModDevGradle's `unitTest`), which a `FluidStack` needs.

`fill-template.sh` deletes `runtime/` from a Library: it is libworks' own.
