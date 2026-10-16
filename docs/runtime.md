# The runtime jar

`runtime/` builds `io.github.5thlayer:libworks-runtime`, the code several Modules share (ADR-0128). A Module nests it with jar-in-jar, so players install nothing extra. It is a small mod, id `libworks`, with a `neoforge.mods.toml` and an `@Mod` entry class (`Libworks`), not a plain library: it registers the Works creative tab. FML loads only the newest nested copy, once.

| Class | For |
|---|---|
| `io.github._5thlayer.libworks.WorksTab` | `KEY`, the `ResourceKey<CreativeModeTab>` of `libworks:works`, the one "Works" tab the whole FactoryWorks suite shares. Its icon is vanilla's iron pickaxe, so it exists without any Module. |
| `io.github._5thlayer.libworks.gallery.GalleryPack` | `register(modBus, modId)`: offers the Module's Gallery pack, `gallery_pack/` in its jar, off by default (ADR 0010); `id`, `build`, `scene` and `clear` name the pack and its functions. |
| `io.github._5thlayer.libworks.transfer.GuardedResourceHandler` | A transfer face with per-slot rules. NeoForge's `DelegatingResourceHandler` sends slot-less insert and extract past them; this loops through its own slots instead. |
| `io.github._5thlayer.libworks.ghost.Ghost` | `Ghost(ItemStack stack, int x, int y)`: a faded item and where it is drawn, in screen coordinates. |
| `io.github._5thlayer.libworks.ghost.Ghosts` | A screen's ghosts, the items an empty slot would take: positions registered once, each with its candidates, which cycle one a second. No client types, so a game test reads it. |
| `io.github._5thlayer.libworks.ghost.GhostScreen` | `ghosts()`: a screen that shows `Ghosts`, and `ghostAt(mouseX, mouseY)`, the ghost under the mouse now, `ghosts()`' by default. The runtime offers what `ghostAt` finds to EMI, so a Module's screen implements it and registers no EMI provider for its ghosts. No EMI or client types. |
| `io.github._5thlayer.libworks.ghost.emi.GhostsEmiPlugin` | The runtime's `@EmiEntrypoint`: every `GhostScreen`'s ghosts as EMI's hovered stack, not clickable. EMI `compileOnly`, loaded only by EMI (ADR 0007). |
| `io.github._5thlayer.libworks.ghost.client.GhostDraw` | `draw(graphics, font, ghost)`, the one look for the whole suite, and `tooltip(...)`, a ghost's item tooltip. Client-only. |
| `io.github._5thlayer.libworks.machine.client.MachineDraw` | A machine screen's one stand-in look: the panel, recesses, bars, gauges, the fluid tooltip, and text cut to fit. Client-only. |
| `io.github._5thlayer.libworks.machine.FluidContainers` | A machine's drain and fill slots: `drain` and `fill` move a whole container of fluid between a tank and any fluid-capability item, and `holds` and `takes` are the slots' checks. No client types. |
| `io.github._5thlayer.libworks.machine.Tank` | `Tank(id, fluid, capacity, titleKey)`: one of a machine's tanks as Jade shows it. No Jade or client types. |
| `io.github._5thlayer.libworks.machine.jade.MachineReport` | A machine's lines in Jade, such as its status line, from data the server sends. Its `Client` draws them. Jade `compileOnly` (ADR 0006). |
| `io.github._5thlayer.libworks.machine.jade.MachineItems` | A machine's items in Jade, slot by slot, as icons with counts through its `Client`. |
| `io.github._5thlayer.libworks.machine.jade.MachineTanks` | A machine's tanks in Jade, one group each, titled through its `Client`. |

## Using it from a Module

```groovy
dependencies {
    implementation 'io.github.5thlayer:libworks-runtime:[0.1.8,0.2)'
    jarJar(implementation('io.github.5thlayer:libworks-runtime')) {
        version { strictly '[0.1.8,0.2)'; prefer '0.1.8' }
    }
}
```

A Module that uses `libworks.machine` needs 0.1.2 or later, one that calls `fluidTooltip` with a title 0.1.4 or later, one that uses `FluidContainers` 0.1.5 or later, one that uses `GhostScreen` 0.1.6 or later, one that overrides its `ghostAt` 0.1.7 or later, one that runs `WorksTabCheck` from libworks-gametest 0.1.8 or later, one with a Gallery pack 0.1.9 or later, one whose gallery test passes a `GalleryCheck.Layout` or calls `withPack` 0.1.10 or later, one whose gallery has more than 12 scenes 0.1.11 or later, and one with more than 16 0.1.12 or later, so its range starts there. The range is what makes two Modules nesting different patches load one copy: FML keeps the highest version in range. A breaking change bumps the minor, which leaves the range, as ADR 0001 sets for every version here.

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

### Checking it in a game test

The template starts every Module with both: a `WorksTabContents` listener and a `CreativeTabTests` that lists the order (#26).

`io.github._5thlayer.libworks.testkit.WorksTabCheck`, in the separate `libworks-gametest` jar (ADR 0008), is the Works tab game test every Module runs. Each call rebuilds the creative tabs, then succeeds or fails the test:

```java
static void register(MyGameTests.Registrar tests) {
    tests.test("works_tab_holds_every_item_in_order", 1,
            helper -> WorksTabCheck.inOrder(helper, MyMod.MOD_ID, List.of("gear", "belt")));
    tests.test("no_other_tab_holds_a_module_item", 1,
            helper -> WorksTabCheck.noOtherTab(helper, MyMod.MOD_ID));
}
```

`inOrder` fails unless the Works tab shows exactly the listed item paths of that namespace, in that order, and fails on an empty list; `noOtherTab` fails if any tab but the Works tab and the search tab shows one, or if the Works tab shows none. The jar is a `GAMELIBRARY`, never nested. The convention plugin, from 0.3.0, adds it at the runtime's range, compile-only and `localRuntime`, to a Module whose `runtime` is 0.1.8 or later (`docs/plugin.md`), and the `~/.m2` repository the plugin declares serves it (#18). From plugin 0.4.0 only the `gametest` source set compiles against it, so `src/main` cannot name it, and a Module's game tests never ship in its jar (ADR 0011).

## Gallery packs

A Module's Gallery pack (ADR 0010) is `src/main/resources/gallery_pack/`, a datapack of functions only, which the Module offers with one line in its constructor:

```java
GalleryPack.register(modBus, MOD_ID);
```

It shows in the Data Packs screen as `<modid>.gallery.pack` from the Module's lang file, and is never on until a player turns it on there or with `/datapack enable "mod/<modid>:gallery_pack"`. Running a function needs cheats in single player, or operator rights on a server.

### Layout

Every Module's gallery is laid out alike, so a player who has walked one knows the rest:

- `<modid>:gallery/build` runs each scene in README order, the first where the player stands and each further one 24 blocks east of the last: `execute positioned ~24 ~ ~ run function <modid>:gallery/<scene>`. A scene runs on its own from where the player stands, too.
- A scene builds in its plot alone: from `~2` to `~25` east, from the sign's row at `~0` to `~-12` north, 16 high above a stone floor at `~-1`. It clears the plot first, filling it with `structure_void`, then the stone floor, then `air`, since a `fill` that changes nothing fails.
- Its sign stands at the plot's south front, `~13 ~ ~`, as `oak_sign[rotation=0]` with `is_waxed:1b`. Its lines are translation keys, `<modid>.gallery.<scene>` for the feature's name and `<modid>.gallery.<scene>.<n>` for what it shows, in the Module's lang file. A scene of a gesture shows its result, and its sign says how to make it.
- A scene is built working, from the Module, vanilla and the Modules it requires: power, fuel, items and fluid in place. It changes blocks, their contents, and the Module's own records for what it places; never time, weather, game rules or players.
- `<modid>:gallery/clear`, which a pack may leave out, clears one scene's plot from where the scene runs, removing what it placed as a player's world would: entities first, then the Module's blocks, so its records of them go too. The game tests run it on each plot before clearing the plots themselves, which sets blocks with no removal and would leave those records for the next test's scene to find.
- A gallery has at most `GalleryCheck.MAX_SCENES` (24) scenes, which bounds the plots the check force-loads and clears, with room above the largest gallery. It is built beside the test's structure, so any structure serves.
- A command fails only by throwing, as vanilla's do; one that reports a failure and returns 0 passes the check unseen.

### Checking it in a game test

`io.github._5thlayer.libworks.testkit.GalleryCheck`, in libworks-gametest, is the gallery test every Module runs. `builds` turns the pack on, runs the build as an operator player standing at `origin`, then clears the plots and turns the pack off, passing or failing. Turning a pack on reloads the server's data under every test running with it, so the test is registered in a batch of its own: its own `TestEnvironmentDefinition`, as Voidworks' `testOnItsOwn` registers one:

```java
// a Module's own helper, registering the test under an environment no other test uses
tests.testOnItsOwn("the_gallery_builds_every_scene", GalleryCheck.TICKS,
        helper -> GalleryCheck.builds(helper, MyMod.MOD_ID, new BlockPos(0, 1, 8), List.of(
                new GalleryCheck.Scene("harvester", new BlockPos(13, 0, -6), MyBlocks.HARVESTER.get()),
                new GalleryCheck.Scene("oil_well", new BlockPos(13, 0, -6), MyBlocks.OIL_WELL.get()))));
```

It fails when the pack is not offered or is on by default; when a function under `<modid>:gallery/` failed to load, naming the line that did not parse, as a block that no longer exists makes it; when a listed scene has no function; when a command in the build fails, naming it, in a scene function too; and when a scene's key block, given from where that scene runs, is not there or lies outside its plot.

A gallery whose build lays its scenes in rows, so the far ones stay within simulation distance of where it is run, gives `builds` its `GalleryCheck.Layout`: scenes a row, each a plot (24 blocks) east of the last, and how far north each row is of the one before, at least a plot's depth (13). Scene `i` runs from `origin.north(rowDepth * (i / perRow)).east(24 * (i % perRow))`, and the key blocks, the plots force-loaded and the plots cleared follow it. Without one, `builds` checks a single row, `Layout.ONE_ROW`.

```java
// build runs pumps at ~, ~24 east and ~48 east, then the next row from ~ ~ ~-16
GalleryCheck.builds(helper, MyMod.MOD_ID, new BlockPos(0, 1, 8), new GalleryCheck.Layout(3, 16), scenes);
```

A test of the Module's own that needs the pack, such as a scene doing its work over time or a sign replaying its scene, runs in `withPack`, the same frame `builds` runs its checks in. It turns the pack on over the gallery's first `plots` plots and loads them; adds the test's own steps to the sequence, given an operator player's source standing at `origin` to build from; then runs `gallery/clear` on each plot when the pack has one, clears the plots and turns the pack off, passing or failing. It returns the test's sequence once the pack is off, which the test ends, so it can check what clearing left too. It turns the pack on, so it is registered in a batch of its own as `builds` is:

```java
GalleryCheck.withPack(helper, MyMod.MOD_ID, new BlockPos(0, 1, 8), new GalleryCheck.Layout(3, 16), 1, (source, steps) -> steps
        .thenExecute(() -> GalleryCheck.assertRuns(helper, source, GalleryPack.scene(MyMod.MOD_ID, "pump")))
        .thenWaitUntil(() -> helper.assertTrue(level.getFluidState(lava).isEmpty(), "the Pump has not taken its lava yet")))
        .thenSucceed();
```

A scene run on its own from `origin` stands on the first plot. `assertRuns`, `assertKeyBlocks`, `select` and `operator` are there for checks of a Module's own.

libworks' own sample gallery, a furnace and a hopper, is in `runtime/src/gametest/resources/gallery_pack/`, and `sh ./gradlew :runtime:runClient` offers it.

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

// in JEI's glue, on a GhostScreen (below); EMI's glue is the runtime's
screen.ghostAt(mouseX, mouseY).map(Ghost::stack);
```

A ghost stands at the slot's `x` and `y`, or the `x` and `y` given, plus the suppliers' corner, read each time, so it follows a screen that moves on resize; a `new Ghosts()` has the corner at 0, so its positions are screen coordinates. `Ghost`s are in screen coordinates, so `GhostDraw.draw` goes in a pass drawn in them (`extractBackground`, or after `super.render`), not in `extractLabels`, which is translated to the panel's corner. A slot that holds an item, or is inactive, shows no ghost. A position with several candidates shows one for `Ghosts.CYCLE_MILLIS` (1000) and then the next, picked by the `millis` passed in; `Ghosts` never reads the clock. `at` takes the mouse as `double`, which an `int` caller passes as is.

`add(Slot)` scans the item registry once, on first use, and keeps the result. Any other candidates that cost a scan go through `Ghosts.cached(...)`. What a slot takes is the Module's to decide. JEI glue stays in the Module; EMI's is the runtime's (below).

## Ghosts in EMI

A screen that implements `GhostScreen` has its ghosts in EMI with nothing else to register:

```java
public class BoilerScreen extends AbstractContainerScreen<BoilerMenu> implements GhostScreen {

    private final Ghosts ghosts = new Ghosts(() -> leftPos, () -> topPos);

    @Override
    public Ghosts ghosts() {
        return ghosts;
    }
}
```

A screen with a ghost beyond `ghosts()`, such as Craftworks' Assembler, whose Held recipe's product stands at the panel's head drawn as the item itself and only hit-tested, overrides `ghostAt` to find that one too:

```java
@Override
public Optional<Ghost> ghostAt(double mouseX, double mouseY) {
    Optional<Ghost> over = head.at(mouseX, mouseY, Util.getMillis());
    return over.isPresent() ? over : GhostScreen.super.ghostAt(mouseX, mouseY);
}
```

The runtime's own `@EmiEntrypoint`, `GhostsEmiPlugin`, registers one generic stack provider: over a `GhostScreen`, the ghost its `ghostAt` finds is EMI's hovered stack, one of its item whatever count it is drawn with, so the recipe and usage keys work on it as on a real stack. It is not clickable, so a left click goes past it to the empty slot under it, where a stack in hand goes in; a clickable one would have EMI take the click to show recipes. Off every ghost it hands EMI nothing, and EMI's own slot lookup answers for a real stack.

The Module registers no stack provider for these ghosts. EMI asks a screen's own providers before the generic one, so a Module's old provider for the same screen keeps answering until the Module drops it: drop it, with its `new EmiStackInteraction(...)`, when moving onto `GhostScreen`. `GhostScreen` names no EMI type, and only EMI loads `GhostsEmiPlugin`, by its annotation, so a screen implements it whether or not EMI is installed and the runtime loads without EMI (ADR 0007). The runtime's mods.toml has no dependency on EMI. JEI needs no such provider: it never takes a click on an ingredient a screen's handler names, so its glue stays in the Module, and asks the screen's `ghostAt`.

Jade and EMI are the compat mods the runtime builds against, both `compileOnly` at the versions the Modules build against: only a Module's own plugin loads Jade's providers (ADR 0006), and only EMI loads the runtime's EMI plugin (ADR 0007).

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

`panel` recesses only active slots and draws the ghosts over them at the `millis` passed in, as `Ghosts` never reads the clock; the screen keeps its own `Ghosts`. A bar's `fraction` is clamped to 0–1, and its label, white with a shadow, is drawn centred when it fits the width less 4 px and the bar is at least `BAR_HEIGHT` (12) tall; an empty `Component` draws none. A gauge is always `BAR_HEIGHT` tall, tiles the fluid's still texture tinted as in the world over the filled part, and writes the amount in mB when a full gauge's label (`<capacity> mB`) fits, so a narrow gauge shows none at any fill; an empty stack draws only the recess. `fluidTooltip` shows the fluid's name, or "Empty", and the amount out of the capacity; its overload with a `title` puts the tank's name first, for a machine whose tanks are named, such as a Boiler's water and steam. The runtime owns only the fluid tooltip's two strings, `libworks.machine.fluid_empty` and `libworks.machine.fluid`; bar labels and tank titles come from the Module. `wholeOnHover(graphics, font, line, width, mouseX, mouseY)` shows a line `fitted` cut, whole.

## Machines in Jade

The runtime builds against Jade but registers nothing (ADR 0006). A Module's own `@WailaPlugin` builds the providers, with uids in its own namespace, and registers them; the runtime's game-test server runs without Jade, which keeps the runtime from loading a Jade class itself.

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

## Drain and fill slots

A **drain slot** takes a filled container, a bucket or any item with NeoForge's fluid item capability (`Capabilities.Fluid.ITEM`), and empties it into a tank; a **fill slot** takes an empty one and fills it from a tank. Either way the container then goes to an output slot. `FluidContainers` does the move, in a machine's tick:

```java
ItemStack after = FluidContainers.drain(items.get(WATER_IN), items.get(BUCKETS_OUT), waterTank);
if (after != null) {
    items.get(WATER_IN).shrink(1);
    items.set(BUCKETS_OUT, after);
}

// a fill slot: FluidContainers.fill(items.get(STEAM_IN), items.get(STEAM_OUT), steamTank, FluidResource.of(steam))

// the slots' checks, such as in isItemValid
case WATER_IN -> FluidContainers.holds(stack, fluid -> fluid.getFluid() == Fluids.WATER);
case STEAM_IN -> FluidContainers.takes(stack, FluidResource.of(steam));
```

A container goes whole or not at all. `drain` and `fill` return null, and change nothing, while the tank has too little room or too little of the fluid for the whole container, while the output holds something the container can't stack onto, or when the stack is empty or no fluid container. They work on a copy of the two slots inside a root transaction of their own, so a machine's own slots need not be transactional and they are called outside any open transaction, as from a tick; and they move one container a call: the caller takes one from the container's slot and sets the output to what was returned. `fill` draws only the fluid it names, whatever else the tank holds. `holds` is true for a container holding any fluid its predicate accepts; `takes` for an empty one that fluid can go into.

## Language

The runtime's own terms. They are not in `GLOSSARY.md`, which `fill-template.sh` copies into every new Module.

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
3. `sh ./gradlew :runtime:publishToMavenLocal`, which publishes `libworks-runtime` and `libworks-gametest` at that version, and refuses a version already in `~/.m2`: a published version never changes, so a fix is the next patch
4. tag `runtime-v<version>`, annotated with both jars' sha256, as the Modules' `v<version>` tags are: `git tag -a runtime-v<version> -m "libworks-runtime <version>" -m "$(cd ~/.m2/repository/io/github/5thlayer && sha256sum libworks-runtime/<version>/libworks-runtime-<version>.jar libworks-gametest/<version>/libworks-gametest-<version>.jar)"`

Nothing is pushed or uploaded without the user's word. `git tag -l 'runtime-v*' -n9` lists the releases.

The runtime's own game tests (`sh ./gradlew :runtime:runGameTestServer`, also run by the root's `runGameTestServer`) live in `runtime/src/gametest`, outside the jar: the tab is registered, `GalleryCheck` on the sample gallery and on fixtures that must fail it, ghosts, and a `Tank` built on a server with no Jade (ADR 0006); the server has no EMI either, so it loads the runtime without EMI (ADR 0007). Its JUnit tests run under FML (ModDevGradle's `unitTest`), which a `FluidStack` needs.

`fill-template.sh` deletes `runtime/` from a Module: it is libworks' own.
