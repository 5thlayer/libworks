---
status: accepted
---

# A jar ships nothing of Mojang's and uses vanilla assets only at runtime

Minecraft's EULA lets a player who owns Java Edition make and distribute Mods, and defines one as
"something original that you or someone else created that doesn't contain a substantial part of our
copyrightable code or content" ([Minecraft EULA](https://www.minecraft.net/en-us/eula), read on
2026-10-16). Every 5thlayer Module is MIT-licensed, with its art under CC BY 4.0 where it has its
own (Beltworks ADR 0001), and Mojang's code and content can be relicensed under neither. Until now
the rule was written down only in Voidworks' spec, for the Void Dragon's look (5thlayer/voidworks#5).

**Decision.** A 5thlayer jar ships nothing of Mojang's: no vanilla texture, no recolour or edit of
one, and no model or rendering code copied out of the game. "A substantial part" is the EULA's
line; a Module draws its own at none, so no one has to judge how much is too much. A Module may use
vanilla models and textures at runtime, read from the player's own game, where the jar holds none of
them: it may bake a vanilla model layer and draw its texture tinted, as Voidworks' spec has the Void
Dragon do, or tint a vanilla block or item model through a `tintindex` and a colour handler. A Module's
own art may be painted onto a vanilla model's UV layout, the model itself read at runtime.
A Module's icon and gallery images are its own art too (`docs/agents/publishing.md`).

This is a reading of the EULA's Mods section, not legal advice.

**Considered.** Shipping recoloured vanilla textures: the simplest way to a family look, but the
result is still Mojang's content, and a CC BY 4.0 label on it would claim a licence no Module can
grant. Allowing a small share of Mojang's content under the EULA's "substantial part": every
texture would need a judgement of its own, and the licence problem stays at any size.

## Consequences

- A Module's `REUSE.toml` marks its asset globs CC-BY-4.0, so a vanilla texture in the repo would
  carry a wrong licence as well as break the EULA. The rule keeps the globs true.
- A look that needs a vanilla texture is built at runtime, by tinting or by layering a Module's own
  art over the vanilla model, never by copying the texture into `src/main/resources`.
