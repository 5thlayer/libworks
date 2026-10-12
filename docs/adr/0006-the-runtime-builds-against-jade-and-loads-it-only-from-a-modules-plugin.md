---
status: accepted
---

# The runtime builds against Jade, and loads it only from a Module's plugin

Fieldworks' Harvester, Wireworks' Boiler and Craftworks' Assembler each carried a copy of the same
Jade view, a status report split so a dedicated server never loads Jade's drawing, an item list and
one titled group per tank, and the copies had begun to drift (#20). libworks-runtime depended on no
compat mod.

**Decision.** libworks-runtime takes Jade `compileOnly`, at the version the Modules build against,
and ships the shared providers in `libworks.machine`. It registers nothing and has no
`@WailaPlugin`: Jade finds a Module's plugin by its annotation, and that plugin builds the runtime's
providers with uids in the Module's own namespace and registers them. So a class that names a Jade
type is loaded only when Jade is installed and has called the Module's plugin, and the runtime runs
without Jade. The runtime's game-test server, which has no Jade, still starts and runs its tests,
which keeps this true.

**Considered.** A copy in each Module, as before: three copies that drift, and a fourth with each new
machine. A plugin in the runtime itself: one uid space for every Module's machines, which Jade's
per-provider config toggles would then share. EMI's ghost stack provider stays in each Module: three
lines a Module, too small to carry a second compat mod across the runtime's version ranges.
ADR 0007 reverses this, after those three lines held the same bug in three Modules.

## Consequences

- A Jade update the Modules take can need a runtime release first, inside the same minor.
- A change to a provider's constructor is a breaking change under ADR 0001, like any other signature.
- libworks-runtime 0.1.2, published before a plugin ADR took the number, calls this ADR 0005 in its sources.
