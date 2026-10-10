# The runtime jar

`runtime/` builds `io.github.5thlayer:libworks-runtime`, the code several Modules share (ADR-0128). A Module nests it with jar-in-jar, so players install nothing extra. It is a library, not a mod: its manifest says `FMLModType: LIBRARY`, and it has no `neoforge.mods.toml`.

| Class | For |
|---|---|
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

## Releasing it

The runtime has its own version, `runtime_version` in `runtime/gradle.properties`, apart from the template's `mod_version`. `sh ./gradlew :runtime:publishToMavenLocal` publishes it to `~/.m2`. A published version never changes, so a fix is the next patch. `scripts/release.sh` is the template's and does not release it.

`fill-template.sh` deletes `runtime/` from a Library: it is libworks' own.
