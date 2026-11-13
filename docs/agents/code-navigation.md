# Code navigation: the ctags symbol index

Find code through `tags`, a Universal Ctags index of `src/main/java`, instead of opening files to look for it. Once found, read enough to understand it: the whole method or class, not a window of lines.

## Setup

The `.claude/hooks/ctags.sh` SessionStart hook rebuilds `tags` every session, and in a cloud session installs Universal Ctags first. Elsewhere, install it yourself (`ctags --version` must say "Universal Ctags"; Exuberant Ctags lacks the options below).

Rebuild it after adding, renaming or moving classes or methods, or after a big refactor:

```sh
ctags -R --languages=Java --fields=+nKSl --extras=+q -f tags src/main/java
```

`tags` is gitignored. It covers `src/main` only: search `src/test` and `src/gametest` with `grep`.

## Usage

Each line is `name<TAB>path<TAB>/^pattern$/;"<TAB>kind<TAB>line:N<TAB>language:Java<TAB>class:Owner<TAB>signature:(...)`. `line:N` is the line number; `class:` names the enclosing type, nested types dotted (`BeltContents.Entry`).

- Where a symbol is defined: `grep -P '^BeltContents\t' tags`. Qualified names work too: `grep -P '^BeltContents\.Added\t' tags`.
- A class's members: `grep -P '\tclass:BeltContents(\t|$)' tags`. Without the `(\t|$)`, the pattern also matches `BeltContentsFoo` and nested types' members.
- Read what you found: from `line:N`, read the whole method, or the whole type for a class or record. Don't read a fixed window of lines around it: answers built from fragments guess wrong about the code around them.
- References: ctags has none, so `grep -rn 'SymbolName' src/main/java`.
- Mixins: search for the target string. Injection targets (`method = "..."`, `@At(target = "...")`) are strings, not references.

## Rules

- Read a whole file when it's under about 300 lines, or when you need most of it. Over that, read the methods and types you need, each in full.
- Rebuild `tags` after adding, renaming or moving classes or methods: a stale `line:N` points at the wrong code.
