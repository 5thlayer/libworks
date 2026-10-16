# Example Module

One paragraph: the mechanic this Module gives, and which Consumers use it.

## Language

The Module's terms, each with what it is and the words to avoid. `/domain-modeling` adds them as they are resolved.

### Parties

**Example Module**:
This Module.
_Avoid_: Library, Binding

**FactoryWorks**:
The suite of 5thlayer mods this Module belongs to, with the other Modules.
_Avoid_: the -works suite, the Pack

**Consumer**:
A mod that builds against this Module, such as another FactoryWorks Module, or a modpack that loads it.
_Avoid_: client (collides with the game's client side), dependent, integration

### Dev runs

**Game-test pack**:
A datapack only this Module's game tests switch on, holding the data a Consumer would supply, such as a tag's entries or a recipe, so a test can stand on it. Never in what players download.
_Avoid_: test datapack, gametest datapack

**Dev pack**:
A datapack only this Module's dev client switches on, holding the data a Consumer would supply, so a feature a Consumer turns on with data can be tried by hand. Never in what players download.
_Avoid_: debug pack, test pack

### Shipped packs

**Gallery pack**:
A datapack in this Module's jar, off until a player turns it on, whose functions build a working scene of each of the Module's features, each with a sign naming it. Functions only: turning it on changes nothing about how the Module plays (libworks ADR 0010).
_Avoid_: demo pack, showcase pack, gallery datapack
