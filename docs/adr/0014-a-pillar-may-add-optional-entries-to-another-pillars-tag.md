---
status: accepted
---

# A Pillar may add optional entries to another Pillar's tag

A Touch Of ADR-0002, as amended by A Touch Of #6, lets one Pillar add an entry to another Pillar's
tag. It does this with a vanilla tag file that has `replace: false` and marks every entry
`required: false`. Without the other Pillar the tag is never read, so neither Pillar requires the
other. The first use is A Touch of Wickedness shipping
`data/atouchofmadness/tags/entity_type/unsettling.json`, which adds its Bailiff. `checkPlaysAlone`
(ADR 0005, ADR 0013) failed any `data/<ns>` folder in another Pillar's namespace, so that file failed.

**Decision.** A suite may take optional tag entries. A Touch Of does and FactoryWorks does not,
since FactoryWorks ADR-0127 allows none. In such a suite, a Module other than the base may ship
files under another Module's `data/<ns>/tags/`. Each must be a tag whose `replace` is absent or
false, and whose every entry is an object with `"required": false` naming one of the Module's own
ids or tags. The scan reads that namespace's data folder file by file. It reports each file outside
`tags/` and each file that is not a tag, and for a tag it names each bare or required entry, a
`replace: true` and each id that is not the Module's own. The exception does not cover an
`assets/<ns>` folder, the base, a retired id such as the shelved `atouchofcold`, or another
suite's namespace.

**Considered.** Allowing any id an optional entry names, such as `minecraft:zombie`: a Pillar
could then retune another Pillar's tag with vanilla content, which goes further than ADR-0002's
"adds an entry" allows. The exception in every suite: FactoryWorks has not asked for it, and its
ADR-0127 forbids it. A list of allowed files in the plugin: narrower, but every new use would need
a plugin release. A Module declaring its tag files in `libworks { }`: every Module would carry a
line, and the plugin would still need the rule to check it.
