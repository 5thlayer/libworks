#!/bin/sh
# Links into .claude/skills each engineering and productivity skill the mattpocock/skills submodule has and the
# checkout lacks, so a submodule bump brings its new skills with it. diagnosing-bugs stays unlinked: a project skill
# of that name would hide skillworks' one.
cd "${CLAUDE_PROJECT_DIR:-.}" || exit 0
vendor=vendor/mattpocock-skills/skills
linked=
for skill in "$vendor"/engineering/*/ "$vendor"/productivity/*/; do
    [ -f "${skill}SKILL.md" ] || continue
    name=$(basename "$skill")
    [ "$name" = diagnosing-bugs ] && continue
    [ -e ".claude/skills/$name" ] || [ -L ".claude/skills/$name" ] && continue
    ln -s "../../${skill%/}" ".claude/skills/$name" && linked="$linked $name"
done
[ -n "$linked" ] && echo "Linked the vendored skills$linked into .claude/skills: commit the new links."
true
