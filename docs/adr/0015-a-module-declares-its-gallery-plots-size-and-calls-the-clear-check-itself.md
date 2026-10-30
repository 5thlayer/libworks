---
status: accepted
---

# A Module declares its gallery plots' size, and calls the clear check itself

ADR 0010 fixed every scene's plot at 24 wide by 13 deep, and `GalleryCheck` force-loaded and
cleared only that much. Railworks' scenes need plots 40 by 15, and its lines keep clear a block west
and two east past each plot, which its `gallery/clear` clears too. So Railworks rebuilt the geometry
in its own tests: its plot constants, a `withPack` wrapper loading and clearing what libworks' plot
left out, and a test that its `gallery/clear` empties the plot. Its scenes' reach went unnoticed
until a neighbouring game test left a block in it (#40).

**Decision.** A Module whose plots differ declares their size, `GalleryCheck.PlotSize(width, depth,
reachWest, reachEast)`, in the `Layout` it gives the check; `PlotSize.DEFAULT` is ADR 0010's 24 by
13 with no reach, and a `Layout` without one takes it. The reach is part of the plot: the key-block
check, the plots force-loaded and the plots cleared all cover it, a row's plots are their width and
reach either way apart, so one plot's reach never lies over the next, and rows are at least a plot's
depth apart. A plot reaches west no further than the column east of where the scene runs, where the
player stands.

`GalleryCheck.clears` proves a pack's `<modid>:gallery/clear` empties the declared plot: the first
plot filled with glass, the clear run on it, then the floor block across its bottom layer and air
above. It is a test of its own that the Module registers, not a step of `builds`.

**Considered.** Running the clear check inside `builds` whenever the pack has a `clear`: every
Module would get it with no code, but not every Module's plot stands on stone (Voidworks' stands on
smooth stone, which its Displacers do not harvest), so `builds` would need the floor too, and a
Module whose clear does not yet empty its plot would go red on a patch release, which ADR 0001
reserves for additions. The fixture libworks' own rows gallery uses, a `clear` that only marks the
plot it ran on, would fail as well. Laying a row's plots their width apart, with the reach lying over
the neighbours' ends: a scene's clear would then empty the end of the plot west of it, and a
neighbour's blocks would stand in the space a plot's reach keeps clear.

## Consequences

- A Module with a `gallery/clear` registers one more test, in a batch of its own as `builds` is:
  `GalleryCheck.clears(helper, modId, origin, layout)`, or with the floor block when it is not
  stone. `assertClears` is the same check for a test that is already in `withPack`.
- A Module with default plots changes nothing.
- A Module that lays more than one wide plot a row runs each further scene at the plot's spacing,
  `width + reachWest + reachEast` east of the last, in its `gallery/build`.
