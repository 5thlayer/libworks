---
status: accepted
---

# Below 1.0 an addition bumps the patch

This ADR belongs to 5thlayer/libworks, the template every 5thlayer Library starts from, and every
Library inherits it unchanged. A Library does not restate it: a Library started from the template
keeps this file as its ADR 0001 and numbers its own from 0002, and a Library whose ADRs predate the
template points to this one instead
(<https://github.com/5thlayer/libworks/blob/main/docs/adr/0001-below-1-0-an-addition-bumps-the-patch.md>).
It is Groundworks' ADR 0001 as amended, which Craftworks' ADR 0008 restated.

**Decision.** A Library's version is semver from 0.1.0. Below 1.0, only a breaking change bumps
the minor version. An addition or a fix bumps the patch.

A breaking change is one that could make something that worked before stop working, or behave
differently, with no change of its own. That covers what a Consumer or a pack author depends on:

- the code another mod calls: its types, methods and events
- a pack's data and config: a field's, a key's or a tag's name, meaning or default
- a player's save: something saved by one version that another can't read back

A change that only moves the pace of play, such as a default duration, is not breaking: the pack
author's own values still win. A release that reads an older save in a new form is still a patch, as
long as the old saves load.

The version is the Library's alone. The Minecraft version goes in the jar's name,
`<archives_name>-<minecraft_version>-<mod_version>.jar`, and not in the version.

## Why the patch

A Consumer that nests a Library declares a version range up to the next minor, because the jar one
Consumer nests and the one another depends on resolve to a single loaded version. An addition then
lands inside every Consumer's range, and no Consumer needs a release of its own to pass it on.
Groundworks' 0.2.0 to 0.4.0 were additions that bumped the minor, and each one forced a Beltworks
release before the Pack could load it.

A Library that nothing nests, which the Pack pins at an exact version, gains nothing from a range.
The rule holds there too, so that the minor still means one thing on every Library: read the
changelog before moving the pin.

## Consequences

- A minor release's changelog names what a Consumer or pack author has to change.
- A published version never changes; a fix is the next patch (`docs/agents/releases.md`).
