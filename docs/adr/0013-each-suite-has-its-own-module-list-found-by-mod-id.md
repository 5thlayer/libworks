---
status: accepted
---

# Each suite has its own Module list, found by mod id

ADR 0005 gave the plugin one list, FactoryWorks' Modules. A Touch Of is a second suite of Modules
built with the plugin, called Pillars (`atouchofdarkness`, `atouchofmadness`, `atouchofcold`).
Each plays alone in vanilla and nests the suite's Runtime, `atouchof` (A Touch Of ADR-0001,
ADR-0002). Against the one list, a Pillar was checked against the FactoryWorks Modules it never
names, and never against the other Pillars.

**Decision.** The plugin holds one list per suite, `PlaysAlone.SUITES`. Each suite has its Modules,
the base among them that every one may name and require, and the retired ids none may name:
FactoryWorks with Groundworks as base and `factoryworks` retired, and A Touch Of with its Runtime
`atouchof` as base. A Module's suite is the one whose list holds its `mod_id`, so a Module states
nothing. It is checked only against its own suite, so a Pillar may name `pipeworks:pipe` and a
FactoryWorks Module may name a Pillar's id. A mod that no suite lists, such as the template's
`examplelib` or a repo just filled from it, is checked against every suite's Modules but the bases,
since its suite is not known. A new Module or Pillar still reaches the others through a plugin
release they move to (ADR 0005).

**Considered.** A `libworks_suite` key in `gradle.properties`, or `suite` in the `libworks { }`
block: explicit, but every Module would carry a line that can disagree with the plugin's list, and
the list still has to name the Module. Inferring the suite from the mod id's prefix, so that any
`atouchof<danger>` is a Pillar: a new Pillar would need no plugin release, but it drops ADR 0005's
explicit list, and an outside mod could share the prefix. Failing on a mod no suite lists: the
template's own build would need an exception.
