---
status: accepted
---

# A request to steward a PR is the user's word to push it

Every Module's CLAUDE.md says nothing is pushed without the user's word. skillworks' `steward` skill,
moved there from Beltworks for every Module (skillworks #15), drives a PR to green: it rebases and
force-pushes its branch, pushes fixes for review findings, and merges once CI and `/code-review` are
clean. The skill defers to the checkout's CLAUDE.md, so under that rule alone it could do none of
this without asking at each step.

**Decision.** The template's CLAUDE.md says that asking a session to steward a PR is the user's word
for that PR's pushes, its rebases onto `main` and its merge, under the `steward` skill. Every other
push still waits for the user, and releases still follow `docs/agents/releases.md`.

**Considered.** The skill overriding CLAUDE.md: one source for the rule, but a skill silently
loosening a checkout's own rules. The skill proposing each push and the merge: no rule changes, but
a session the user asked to land a PR stops at every step to ask.

## Consequences

- `template-drift` reports a Module whose CLAUDE.md does not name the `steward` skill, and a Module
  with a `.claude/skills/steward` of its own, which would hide skillworks' one.
- A Module that wants its PRs only ever merged by hand drops the line, and its drift is intended.
