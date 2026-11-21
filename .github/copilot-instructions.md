# Copilot instructions

The full rules are in `AGENTS.md` at the repository root. Read it first. The essentials:

- This is a proof of concept for an offline Android voice capture app. Evidence comes before features: see `docs/gates.md`.
- Work on one `STEP-*` at a time (`docs/plan/steps.md`). Reference `STEP-*`, `REQ-*`, `GATE-*`, `DEC-*` IDs in commits.
- `android/core` is pure Kotlin/JVM with no Android imports and no third-party runtime dependencies.
- Never invent measurements or library APIs. Label unverified claims `UNVERIFIED`.
- Never stage or commit (`git add`, `git commit`, ...) unless a human explicitly asks. Leave changes in the working tree.
- Never commit audio, models or secrets. Never mark a gate PASS; humans decide gates.
- No network calls in the app's core path.
- Role agents are generated from `agents/`; never hand-edit `.github/agents/` or `.claude/agents/`.
