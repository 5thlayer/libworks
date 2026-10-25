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
nothing. It is checked against its own suite's Modules but its base, and against every other
suite's Modules with their base, since a base is shared only inside its suite: a Pillar may not
name `pipeworks:pipe` or `groundworks:wrench`, and a FactoryWorks Module may not name a Pillar or
the Runtime `atouchof` (A Touch Of ADR-0001, ADR-0002, FactoryWorks ADR-0127). A mod that no
suite lists, such as the template's `examplelib` or a repo just filled from it, is checked
against every suite's Modules but the bases, since its suite is not known. A new Module or Pillar still reaches the others through a plugin
release they move to (ADR 0005).

**Considered.** A `libworks_suite` key in `gradle.properties`, or `suite` in the `libworks { }`
block: explicit, but every Module would carry a line that can disagree with the plugin's list, and
the list still has to name the Module. Inferring the suite from the mod id's prefix, so that any
`atouchof<danger>` is a Pillar: a new Pillar would need no plugin release, but it drops ADR 0005's
explicit list, and an outside mod could share the prefix. Failing on a mod no suite lists: the
template's own build would need an exception.

**Amended 2026-10-25 (plugin 0.4.4).** Plugin 0.4.3 checked a Module only against its own suite, so a Pillar
could name any FactoryWorks Module and a FactoryWorks Module any Pillar, which neither suite's rule
allows. 0.4.4 adds every other suite's Modules, bases included, as above.
