#!/bin/sh
# Rebuilds the ctags symbol index `tags` over src/main/java, which docs/agents/code-navigation.md tells agents to search
# instead of reading whole files. A cloud session installs Universal Ctags first; a local machine without it is left
# alone. Never fails the session: a missing index only costs the agent some reading.
# Runs in the checkout the hook cd-ed into: the session's own, a worktree included.
universal() { ctags --version 2>/dev/null | grep -q 'Universal Ctags'; }

if ! universal && [ "${CLAUDE_CODE_REMOTE:-}" = "true" ]; then
  { apt-get install -y -qq universal-ctags || { apt-get update -qq && apt-get install -y -qq universal-ctags; }; } \
    >/dev/null 2>&1
fi

if universal; then
  ctags -R --languages=Java --fields=+nKSl --extras=+q -f tags src/main/java 2>/dev/null
fi
true
