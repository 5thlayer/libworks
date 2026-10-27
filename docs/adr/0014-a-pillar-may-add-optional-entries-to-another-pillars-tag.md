---
status: accepted
---

# A Pillar may add optional entries to another Pillar's tag

A Touch Of ADR-0002, as amended by A Touch Of #6, lets one Pillar add an entry to another Pillar's
tag. It does this with a vanilla tag file that has `replace: false` and marks every entry
`required: false`. Without the other Pillar the tag is never read, so neither Pillar requires the
other. The first use is A Touch of Wickedness shipping
`data/atouchofmadness/tags/entity_type/unsettling.json`, which adds its Bailiff. `checkPlaysAlone`
(ADR 0005, ADR 0013) failed any `data/<ns>` folder in another Pillar's namespace, so that file
failed.

**Decision.** Each suite states what a Module may ship in another Module's tags: A Touch Of takes
optional entries, and FactoryWorks takes none, since FactoryWorks ADR-0127 allows none. In a suite
that takes them, a Module other than the base may ship files under another Module's
`data/<ns>/tags/<registry>/`. Each must be a tag whose `replace` is absent or false, with no
NeoForge `remove` list, which would take entries out of the other tag, and whose every entry is an
object with `"required": false` naming one of the Module's own ids or tags. The scan reads that
namespace's data folder file by file, and reports each file that fails with why: outside `tags/`,
not a tag or not valid JSON, a bare or required entry, an entry with no id, a `replace` that is
true or not a boolean, a `remove`, or an id that is not the Module's own. The exception does not
cover an `assets/<ns>` folder, the base, a retired id such as the shelved `atouchofcold`, or
another suite's namespace.

`atouchofcold` is retired while A Touch of Cold is shelved (A Touch Of #5), so its own build would
fail: no suite lists it, and its ids are forbidden. Reviving it means a patch release that moves it
from the suite's retired ids back to its Pillars.

**Versioning.** Forbidding `atouchofcold` and `atouchofwickedness` tightens the check, but no
Module ships either, so none has to edit its build, and the release is a patch (ADR 0001), as
0.4.4's tightening was (ADR 0013).

**Considered.** Allowing any id an optional entry names, such as `minecraft:zombie`: a Pillar
could then retune another Pillar's tag with vanilla content, which goes further than ADR-0002's
"adds an entry" allows. The exception in every suite: FactoryWorks has not asked for it, and its
ADR-0127 forbids it. A list of allowed files in the plugin: narrower, but every new use would need
a plugin release. A Module declaring its tag files in `libworks { }`: every Module would carry a
line, and the plugin would still need the rule to check it.
