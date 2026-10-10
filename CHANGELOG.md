# Changelog

Written for Consumers: what a mod building against Example Library can use, or will see change. A published version never changes: a fix is the next patch (`docs/agents/releases.md`).

## Unreleased

- `libworks-runtime` 0.1.0, a runtime mod (id `libworks`) a Module nests, FML loading the newest copy once. It registers the shared creative tab `libworks:works`, titled "Works", whose key is `WorksTab.KEY`, and holds `GuardedResourceHandler`, a `DelegatingResourceHandler` whose slot-less insert and extract honour a subclass's per-slot refusals (`docs/runtime.md`, #8).
