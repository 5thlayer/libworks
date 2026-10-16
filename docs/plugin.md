# The convention plugin

`plugin/` builds `io.github.5thlayer:libworks-plugin`, the Gradle plugin `io.github.5thlayer.libworks.module` every Module applies (ADR 0003). It carries the whole Module build: ModDevGradle on Java 25, the game test server that names each test, the jar's licensing, `~/.m2`-only publishing, the `libworks { }` upstream ranges and runtime, and the `release`, `upload` and `buildGametestStructures` tasks with the scripts they run. The template builds it from `plugin/` as an included build; a Module resolves it from `~/.m2`:

```groovy
// settings.gradle
pluginManagement { repositories { mavenLocal(); gradlePluginPortal() } }
// build.gradle
plugins { id 'io.github.5thlayer.libworks.module' version '0.1.2' }
```

Its CI is `.github/workflows/module-ci.yml`, called at the same tag: `uses: 5thlayer/libworks/.github/workflows/module-ci.yml@plugin-v<version>`. `fill-template.sh` writes both at the version `plugin/gradle.properties` holds. `module-ci.yml` reads the version from that `uses:` ref and fails when `build.gradle` applies another, so the two move together.

Beside the build and `reuse lint`, it runs `python3 scripts/build-textures.py --check`, with Pillow, when the Module has that script, and skips it otherwise. The script is the Module's own: it builds the textures it commits, typically from third-party art kept unmodified under `data/art/`, and `--check` fails when a committed one differs from what it builds. The art is credited in `NOTICE` and licensed by an override stanza in `REUSE.toml`, whose commented example the template ships.

An upstream is declared as `upstream('<name>') { range = '[0.5.4,0.6)' }`, with `tagPrefix` when its tags are not `<name>-v<version>`. The range's lower bound is the version the Module prefers and the one CI builds from its tag, so it must be a released version.

An upstream with `optional = true` goes on `localRuntime`, a configuration the plugin creates (or takes over, when the Module declares one) and `runtimeClasspath` extends: the dev runs load it, and the published jar, pom and module metadata do not depend on it. `libworksUpstreams` still lists it, so CI builds its tag, and `-PsiblingBuilds` still checks its range.

`libworksUpstreams` prints one line per upstream, `<repo> <tag>`, which `module-ci.yml` hands `.github/scripts/build-upstreams.sh` to publish each to the runner's `~/.m2`. With `runtime` set it adds `5thlayer/libworks runtime-v<version> :runtime`: a third field names the one Gradle project to publish from that checkout (`./gradlew :runtime:publishToMavenLocal`), which is not recursed into. Each line is built once.

With `runtime` 0.1.8 or later, the plugin also puts `libworks-gametest` (ADR 0008) in the same range on `libworksGametest`, which the `gametest` source set's `gametestCompileOnly` and `localRuntime` extend: the game tests compile against it and the dev runs load it, `src/main` cannot name it (ADR 0011), and the jar, pom and module metadata never name it. The runtime's tag publishes it beside the runtime, so CI needs nothing more. A game test server on a source set of its own, without `localRuntime`, as Craftworks' and Fieldworks' `solo` runs are, extends `libworksGametest` itself: `soloRuntimeOnly.extendsFrom implementation, runtimeOnly, libworksGametest`, and, from 0.4.0, `soloCompileOnly.extendsFrom libworksGametest` when that source set compiles game-test code of its own.

`processResources` expands `META-INF/neoforge.mods.toml` with `mod_id`, `mod_name`, `mod_description`, `version`, `minecraft_version`, `neoforge_version`, each upstream's range as `${<name>_range}`, the Maven range the dependency uses: `versionRange = "${groundworks_range}"`, and, with `runtime` set, its range as `${libworks_runtime_range}`.

The plugin adds one repository, `~/.m2`, as `exclusiveContent` for the whole `io.github.5thlayer` group (#18): every 5thlayer module resolves from there and from no other repository, unfiltered ones such as `https://cursemaven.com` included, and `~/.m2` serves nothing else. The filter is by group, so an upstream's own upstreams and Modules only the dev client loads, through `clientMods`, pass with no entry. A Module declares no `~/.m2` repository of its own: one in either form, `mavenLocal { content { … } }` or `exclusiveContent { forRepository { mavenLocal() } … }`, is refused naming it, since a second exclusive `~/.m2` would shut the plugin's out and leave Gradle finding the Modules nowhere.

The plugin adds a `gametest` source set to the mod, which the dev runs load and the jar leaves out (ADR 0004): `src/gametest/resources` holds the Module's dev pack, `dev_pack/`, and game-test pack, `gametest_pack/`, and `src/gametest/java` the code that switches them on, built against the Module's own code and dependencies. From 0.4.0 it also holds all of the Module's game-test code and test-only resources, which never ship (ADR 0011): the `*GameTests` registrar, an `@EventBusSubscriber` that takes the mod bus from `FMLConstructModEvent`, its test classes, and `structure/gametest/`. Every run gets `libworks.run`, its name: the template's `DevPacks` adds `dev_pack/` when it is `client` and `gametest_pack/` when it is `gameTestServer`. A Module that also adds the source set to its mod, `sourceSet sourceSets.gametest` in `neoForge.mods`, is refused naming that line, where ModDevGradle would refuse the duplicate only once a run starts.

`writeGameTestServerCommand` builds what `runGameTestServer` needs and writes the game test server's whole command line, one argument a line, to `build/moddev/gameTestServerRunCommand.txt`, without running it: ModDevGradle's args files there leave out the classpath. `runGameTestServer` rewrites it as it starts, so it never lags them. Started from `run/gametest`, the command runs the game tests with no Gradle, as skillworks' `gametest-loop` does to run them many times over; it exits with the number of failed required tests.

`buildGametestStructures` writes the default `gametest/platform.nbt` into `src/gametest/resources/data/<mod_id>/structure/`, leaves an identical one alone, and fails naming the file when the Module has committed a different one: a Module needing another structure commits it (ADR 0003).

`checkPlaysAlone`, which `check` runs, fails when the Module ships something that names another Module (FactoryWorks ADR-0127, ADR 0005): an id in its namespace, such as `pipeworks:pipe`, in a resource or in Java source; an `assets/<ns>` or `data/<ns>` folder; the namespace as a string in Java source, `"pipeworks"`; or a required dependency on it in `neoforge.mods.toml`, where an optional one is allowed. A structure's `.nbt` is read unzipped, so the block ids saved in it count. The resources are read as `processResources` leaves them, so generated ones and the expanded mods.toml count; the code is `src/main`'s Java source, and the `gametest` source set is not read. The Modules are `PlaysAlone.MODULES`: Beltworks, Craftworks, Fieldworks, Gearworks, Groundworks, Pipeworks, Voidworks and Wireworks. Every one but the Module built and Groundworks is forbidden, and the retired `factoryworks`. A new Module is added there, and reaches the others by a plugin release they move to. The failure lists each finding under its folder, such as `build/resources/main/data/beltworks/recipe/x.json names gearworks:gear`.

### Moving to 0.4.0

0.4.0 breaks a Module whose `src/main` still names libworks-gametest, which no longer compiles there (ADR 0011). A Module moving to it, as `template-drift` carries it out:

- moves its game-test code, `gametest/` under its package, to `src/gametest/java`, and `data/<mod_id>/structure/gametest/` to `src/gametest/resources`;
- deletes the `*GameTests.register(modBus)` line from its `@Mod` class, and makes the registrar an `@EventBusSubscriber(modid = ...)` whose `@SubscribeEvent` handler for `FMLConstructModEvent` hands `event.getContainer().getEventBus()` to its `register`;
- deletes its `<modid>.gametest` system property and any `isGametestEnabled()` check around registration: nothing on the game-test side is gated;
- moves a tag entry or other data a test needs out of a shipped file into its Game-test pack, and deletes the lang lines of test-only blocks;
- gives shipped code that called test code, such as Beltworks' `ServerLevelUpdateMixin`, a hook with no test vocabulary that the game-test side fills;
- adds `soloCompileOnly.extendsFrom libworksGametest` to a `solo` source set that compiles game-test code, which `compileOnly` no longer hands it;
- points its `PlaysAloneTest`, and anything else that reads `src/main/resources/data/<mod_id>/structure/gametest`, at the new path.

## Releasing it

The plugin has its own version, `plugin_version` in `plugin/gradle.properties`, apart from the template's `mod_version` and the runtime's `runtime_version`, and its own release path: the template's `release` task does not release it, and `release-train` does not cover libworks. ADR 0001 sets the bump by what it does to a Module: a change that makes a Module edit its build is breaking. From a clean main:

1. set `plugin_version` and commit `chore(plugin): release <version>`
2. `sh ./gradlew build runGameTestServer` and `python3 -m unittest discover scripts/tests`
3. `sh ./gradlew -p plugin publishToMavenLocal`, which refuses a version already in `~/.m2`: a published version never changes, so a fix is the next patch
4. tag `plugin-v<version>`, annotated with the jar's sha256: `git tag -a plugin-v<version> -m "libworks-plugin <version>" -m "sha256 $(sha256sum ~/.m2/repository/io/github/5thlayer/libworks-plugin/<version>/libworks-plugin-<version>.jar | cut -d' ' -f1)"`

Module CI builds the plugin from that tag, so a Module can move to the version only once the tag is pushed. Nothing is pushed without the user's word. `git tag -l 'plugin-v*' -n9` lists the releases.
