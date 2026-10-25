#!/bin/sh
# Links into .claude/skills each engineering and productivity skill the mattpocock/skills submodule has and the
# checkout lacks, so a submodule bump brings its new skills with it. diagnosing-bugs and implement stay unlinked: a
# project skill of either name would hide skillworks' one.
# Runs in the checkout the hook cd-ed into: the session's own, a worktree included.
vendor=vendor/mattpocock-skills/skills
linked=
for skill in "$vendor"/engineering/*/ "$vendor"/productivity/*/; do
    [ -f "${skill}SKILL.md" ] || continue
    name=$(basename "$skill")
    case "$name" in diagnosing-bugs|implement) continue ;; esac
    [ -e ".claude/skills/$name" ] || [ -L ".claude/skills/$name" ] && continue
    ln -s "../../${skill%/}" ".claude/skills/$name" && linked="$linked $name"
done
[ -n "$linked" ] && echo "Linked the vendored skills$linked into .claude/skills: commit the new links."
true
