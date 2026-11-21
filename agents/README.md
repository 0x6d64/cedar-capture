# agents/

Single source for the role-specific agent definitions. Two tool-specific formats are GENERATED from these files:

- `.claude/agents/<name>.md` - Claude Code subagents
- `.github/agents/<name>.agent.md` - GitHub Copilot custom agents

Edit only the files here, then run (from the repository root):

```bash
python -m voicetools sync-agents          # write the generated files
python -m voicetools sync-agents --check  # fail if they are out of date (CI does this from STEP-40)
```

Never edit the generated files by hand.

## Source format

```
---
name: kotlin-core-engineer          # must equal the file name without .md
description: One line that says what the agent does and WHEN to use it.
claude_tools: Read, Edit, Write, Grep, Glob, Bash
---
Role instructions in Markdown.
```

`_common.md` is prepended to every agent (reading order, hard rules, hand-off note). Keep descriptions on one line: the
generator quotes them for YAML. `claude_tools` restricts what the Claude Code subagent may use; read-only roles omit
Edit and Write. The Copilot files carry no tool list because tool names differ between Copilot hosts (UNVERIFIED):
restrict tools in the host if you need a read-only agent there.

## Roles

| Agent | Use it for | Writes files |
| --- | --- | --- |
| `session-format-guardian` | Anything touching the session spec, parsers or fixtures | yes |
| `kotlin-core-engineer` | Steps for `android/core` (pure JVM) | yes |
| `python-tools-engineer` | Steps for `tools/` | yes |
| `android-app-engineer` | Steps for `android/app` (service, audio, native bridges) | yes |
| `results-scribe` | Turning pasted measurements into results log entries | results log only |
| `reviewer` | Independent review of a change against the project rules | no |
| `doc-auditor` | Cross-checking IDs and numbers across the docs | no |

Use `reviewer` on every change before a human looks at it. An agent must not review its own work.
