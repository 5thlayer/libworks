# Example Library

One paragraph: the mechanic this Library gives, and which Consumers use it.

## Language

The Library's terms, each with what it is and the words to avoid. `/domain-modeling` adds them as they are resolved.

### Parties

**Example Library**:
This Library.

**FactoryWorks**:
The suite of 5thlayer mods this Library belongs to, with the other Libraries.
_Avoid_: the -works suite, the Pack

**Consumer**:
A mod that builds against this Library, such as another FactoryWorks Library, or a modpack that loads it.
_Avoid_: client (collides with the game's client side), dependent, integration

### Dev runs

**Game-test pack**:
A datapack only this Library's game tests switch on, holding the data a Consumer would supply, such as a tag's entries or a recipe, so a test can stand on it. Never in what players download.
_Avoid_: test datapack, gametest datapack

**Dev pack**:
A datapack only this Library's dev client switches on, holding the data a Consumer would supply, so a feature a Consumer turns on with data can be tried by hand. Never in what players download.
_Avoid_: debug pack, test pack
