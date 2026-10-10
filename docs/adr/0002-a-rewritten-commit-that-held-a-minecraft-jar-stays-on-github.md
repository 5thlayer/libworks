---
status: accepted
---

# A rewritten commit that held a Minecraft jar stays on GitHub

Commit `19d30ab`, the first `runtime/` commit, tracked `runtime/build/`, about 99 MB that included
Mojang's `minecraft-patched-26.1.2.77-merged.jar`. The root-anchored `/build/` in `.gitignore` did
not cover a subproject's build directory.

**Decision.** History was rewritten without the build directory and force-pushed (`main` became
`bf09d35`), and `.gitignore` now ignores `build/`, `.gradle/`, `run/` and `logs/` at any depth. The
orphaned commits stay on GitHub. No branch, tag or pull request reaches them, so a clone or fork
does not carry them; they open only by full SHA.

**Considered.** GitHub Support purges only sensitive data that rotating a credential cannot fix,
and the jar is not a credential. Recreating the repository would remove the commits but lose its
pull requests, stars, settings and the links other repositories hold to them. The jar is one
anyone who owns the game rebuilds with the same Gradle task, so the reachable copy exposes nothing
that cost was worth.
