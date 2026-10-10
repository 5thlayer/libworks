# The runtime jar

`runtime/` builds `io.github.5thlayer:libworks-runtime`, the code several Modules share (ADR-0128). A Module nests it with jar-in-jar, so players install nothing extra. It is a small mod, id `libworks`, with a `neoforge.mods.toml` and an `@Mod` entry class (`Libworks`), not a plain library: it registers the Works creative tab. FML loads only the newest nested copy, once.

| Class | For |
|---|---|
| `io.github._5thlayer.libworks.WorksTab` | `KEY`, the `ResourceKey<CreativeModeTab>` of `libworks:works`, the one "Works" tab the whole -works suite shares. Its icon is vanilla's iron pickaxe, so it exists without any Module. |
| `io.github._5thlayer.libworks.transfer.GuardedResourceHandler` | A transfer face with per-slot rules. NeoForge's `DelegatingResourceHandler` sends slot-less insert and extract past them; this loops through its own slots instead. |

## Using it from a Module

```groovy
repositories { mavenLocal() }

dependencies {
    implementation 'io.github.5thlayer:libworks-runtime:[0.1.0,1.0)'
    jarJar(implementation('io.github.5thlayer:libworks-runtime')) {
        version { strictly '[0.1.0,1.0)'; prefer '0.1.0' }
    }
}
```

The range is what makes two Modules nesting different patches load one copy: FML keeps the highest version in range. A breaking change bumps the minor, which leaves the range, as ADR 0001 sets for every version here.

The Pack pins the Module's jar, which carries the nested jar; nothing else is installed.

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

## Releasing it

The runtime has its own version, `runtime_version` in `runtime/gradle.properties`, apart from the template's `mod_version`, and its own release path: `scripts/release.sh` is the template's and does not release it, and `release-train` does not cover libworks. From a clean main:

1. set `runtime_version`, the bump ADR 0001 sets, and commit `chore(runtime): release <version>`
2. `sh ./gradlew build runGameTestServer`
3. `sh ./gradlew :runtime:publishToMavenLocal`, which refuses a version already in `~/.m2`: a published version never changes, so a fix is the next patch
4. tag `runtime-v<version>`, annotated with the jar's sha256, as the Libraries' `v<version>` tags are: `git tag -a runtime-v<version> -m "libworks-runtime <version>" -m "sha256 $(sha256sum ~/.m2/repository/io/github/5thlayer/libworks-runtime/<version>/libworks-runtime-<version>.jar | cut -d' ' -f1)"`

Nothing is pushed or uploaded without the user's word. `git tag -l 'runtime-v*' -n9` lists the releases.

The runtime's own game test (`sh ./gradlew :runtime:runGameTestServer`, also run by the root's `runGameTestServer`) lives in `runtime/src/gametest`, outside the jar, and checks the tab is registered.

`fill-template.sh` deletes `runtime/` from a Library: it is libworks' own.
