@AGENTS.md

# Claude Code notes

- Use plan mode for any change that touches more than one module or any public interface.
- Role subagents are in `.claude/agents/` (generated from `agents/`; do not edit them). Delegate review to `reviewer`.
- Slash commands in `.claude/commands/`: `/next-step [STEP-ID]` picks up a work item, `/gate-report GATE-ID` turns
  pasted measurements into a results log entry without inventing numbers.
- `.claude/settings.json` denies reading `data/` (personal recordings). Pushing is no longer denied there, so the rule
  is in `AGENTS.md`: push only when the human asks, never force-push, and never push anything other than what they named.
- When unsure whether something is verified, write `UNVERIFIED` and say why. Do not guess library APIs; read the
  library source or docs in the repo or ask.
