---
status: accepted
---

# The suite's Module list lives in the plugin

Each Module checked that it plays alone on Groundworks (FactoryWorks ADR-0127) with a copy of the same
JUnit scan, each holding its own list of the other Modules. The copies drifted: a fix to the scan
reached four of five, and Gearworks and Voidworks reached the lists of four (#19).

**Decision.** The convention plugin (ADR 0003) holds the one list of FactoryWorks' Modules, and the
scan: `checkPlaysAlone`, which `check` runs, fails on what a Module ships that names another Module's id,
holds data or assets in its namespace, names it as a string in code, or requires it in
`neoforge.mods.toml`. Every Module but the one built and Groundworks is forbidden, and the retired
`factoryworks` (FactoryWorks ADR-0128). It reads the resources as `processResources` leaves them, so
generated ones count. A new Module reaches the others by a plugin release they move to. The game-test
side of playing alone, and the check of the tags a Module's code reads against its game test, stay in
each Module (#17), in its `gametest` source set since ADR 0011.

**Considered.** A test-fixtures artifact, `libworks-testing`: a third artifact to version and release
beside the plugin and the runtime, and a test in each Module that can still miss a folder it ships. The
template alone: copies picked up by hand, which is how the drift came about.
