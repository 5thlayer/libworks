---
status: accepted
---

# The runtime offers every ghost screen's ghosts to EMI itself

ADR 0006 left EMI's ghost stack provider in each Module, three lines a Module. Those three lines held
a bug each Module found on its own: built as `new EmiStackInteraction(EmiStack.of(stack))` the
interaction is clickable, so EMI takes the left click on a ghost to show its recipes and a stack in
hand never reaches the empty slot under it. Fieldworks and Wireworks fixed it apart, and Craftworks'
Assembler still had it (#23).

**Decision.** libworks-runtime takes EMI `compileOnly`, at the version the Modules build against, and
has an `@EmiEntrypoint` of its own, `ghost.emi.GhostsEmiPlugin`, which registers one generic stack
provider: on a screen that implements `ghost.GhostScreen`, the ghost its `ghostAt` finds under the
mouse, as one of its item, not clickable. `ghostAt` reads the screen's `ghosts()` unless the screen
has a ghost beyond them to find, as Craftworks' Assembler's head is. A Module's screen implements
`GhostScreen` and registers no provider for its ghosts. `GhostScreen` names no EMI type, and only EMI
loads the plugin, by its annotation, so the runtime runs without EMI; its game-test server, which has
no EMI, keeps this true, as for Jade.

**Considered.** A helper each Module's plugin calls: one copy of the interaction, but still a line in
each Module that a new machine can leave out or write by hand. A provider per screen class: EMI asks
those by the screen's exact class, which the runtime never sees. The runtime owning the plugin here,
where ADR 0006 refused it for Jade, costs nothing ADR 0006 feared: a stack provider has no uid or
config toggle, so the Modules share no settings by sharing it. JEI's twin is left out: JEI never takes
a click on an ingredient a screen's handler names, so its glue has no such bug, and it would be a third
compat mod for no fix.

## Consequences

- EMI asks a screen's own providers before the generic ones, so a Module's old provider for the same
  screen still answers until the Module drops it.
- An EMI update the Modules take can need a runtime release first, as a Jade one can.
