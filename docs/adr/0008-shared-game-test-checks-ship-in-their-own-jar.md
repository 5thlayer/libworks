---
status: accepted
---

# Shared game-test checks ship in their own jar

Seven Modules each copied the same Works tab game test, differing only in their items' order (#25).
Moving it into libworks raised where it ships: libworks-runtime is nested in every Module's jar, and
game-test code should not reach players (ADR 0004).

**Decision.** The checks the Modules' game tests share live in the runtime's `testkit` source set,
package `io.github._5thlayer.libworks.testkit`, and publish as `io.github.5thlayer:libworks-gametest`
at `runtime_version`, released with the runtime. Its jar is an FML `GAMELIBRARY`, so it sees
Minecraft's classes without being a mod. A Module takes it `compileOnly` and `localRuntime`, never
`jarJar`: its dev runs have it, its jar does not.

**Considered.** In libworks-runtime: no new artifact, but test-only code in every player's jar. The
convention plugin adding the source to each Module: no new artifact, but the class compiles into the
Module and ships while its game tests do. Settling #17 first, moving game tests out of the jar: the
cleaner home, but a cross-Module change this one need not wait for.

## Consequences

- While a Module's game tests stay in `src/main` (#17), its jar holds a class that names
  libworks-gametest. It loads without it and fails only if run, which the Module's `*.gametest`
  switch prevents. Once #17 moves them out, the dependency moves to the `gametest` source set.
  ADR 0011 settles #17 that way.
- The runtime's own game tests load the testkit as part of the `libworks` mod in dev, not as the
  published jar; a Module's run is the first to load the `GAMELIBRARY` jar itself.
