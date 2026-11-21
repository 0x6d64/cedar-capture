# Working with coding agents

This project mixes work an agent can do alone with work that needs the physical Pixel 7 or a human decision. The
workflow keeps those apart so agents never fabricate evidence.

## Roles

| Role | Does | Never does |
| --- | --- | --- |
| Agent | Code, tests, scripts, docs, draft results entries from pasted numbers | Mark gates PASS, change thresholds, invent numbers, read `data/` |
| Human | Runs anything on the phone, makes recordings, confirms gates, accepts ADRs, chooses models | |

## Agent definitions

Three Claude Code subagents live in `.claude/agents/` and are edited there directly. Implementation work is done by the
main session, which follows `AGENTS.md`; there are no per-module engineer agents.

| Agent | Use it for | Writes |
| --- | --- | --- |
| `results-scribe` | Draft results entries from pasted numbers; proposes labels only | results log |
| `reviewer` | Independent read-only review before a human | no |
| `doc-auditor` | Read-only ID and number consistency audit | no |

An agent never reviews its own change: run `reviewer` as a separate agent.

## The loop

1. Human picks a `STEP-*` (or tells the agent to run `/next-step`).
2. Agent reads `AGENTS.md`, the step, and the IDs it mentions; states a short plan.
3. Agent writes the test first, then the code, runs the checks.
4. Agent hands over: what changed, what it could not verify, what the human must run (exact commands) and which numbers
   to paste back.
5. Human runs the device or measurement part, pastes numbers; agent drafts a results entry (`/gate-report`).
6. Human confirms or rejects the proposed label.

## Review checkpoints (agents stop and wait)

1. After STEP-05: pipeline logic correct with fakes.
2. After STEP-10: 30-minute recording with screen locked works, raw audio safe.
3. After STEP-17: first real go/no-go (speech speed and accuracy).
4. After STEP-26: questions and answers measured on the phone.
5. After STEP-32: spoken answers and hands-free control, or limits documented.
6. After STEP-35: compression usable, or kept manual and desktop-only.

## Hand-off note format

```
Changed: <files and behavior, with IDs>
Checks run: <commands and results>
Not verified: <list>
Human must: <exact commands, which numbers to paste where>
```

## Prompts that work well

- "Run /next-step STEP-03. Stop at the hand-off note."
- "Here are my numbers for GATE-03: <paste>. Run /gate-report GATE-03."
- "Review docs/requirements.md against docs/plan/steps.md and list requirements no step verifies."

## Anti-patterns for this repository

- Letting an agent "estimate" a benchmark number because the phone is not at hand.
- Editing `fixtures/` or `docs/testing/` so a failing test passes.
- Adding a library without an ADR and a license check.
- Building features (streaming, German, Drive upload, embeddings) before the gates pass.
