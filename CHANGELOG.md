# Changelog

Written for Consumers: what a mod building against Example Library can use, or will see change. A published version never changes: a fix is the next patch (`docs/agents/releases.md`).

## Unreleased

- `libworks-runtime` 0.1.0, a runtime mod (id `libworks`) a Module nests, FML loading the newest copy once. It registers the shared creative tab `libworks:works`, titled "Works", whose key is `WorksTab.KEY`, and holds `GuardedResourceHandler`, a `DelegatingResourceHandler` whose slot-less insert and extract honour a subclass's per-slot refusals (`docs/runtime.md`, #8).
- `libworks-plugin` 0.1.0, the Module convention plugin `io.github.5thlayer.libworks.module` (ADR 0003, #11): the shared build (Java 25, ModDevGradle, game test report check, jar licensing, `neoforge.mods.toml` expansion, an immutable `publishToMavenLocal` refused under `-PsiblingBuilds`) and a `libworks { }` block. `upstream('<name>') { range; tagPrefix; repo }` requires a Library strictly in its range, preferring the lower bound, and fails when a `-PsiblingBuilds` checkout (`-P<name>Dir`, `~/minecraft_mods/<name>` by default) falls outside it; `libworksUpstreams` prints `<repo> <tag> <version>` per upstream for CI. `runtime = '<version>'` nests `libworks-runtime` from `<version>` up to the next minor (`0.1.3` gives `[0.1.3,0.2)`), as ADR 0001 has a breaking change bump the minor below 1.0. `tagPrefix` defaults to `<archives_name>-v`.
