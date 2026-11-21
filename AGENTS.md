# AGENTS.md

Instructions for AI coding agents (Claude Code, Copilot agent, Codex and similar) working in this repository.
Humans: see README.md. Claude Code reads CLAUDE.md, which imports this file.

## Project in one paragraph

An offline Android app (Kotlin, target device Google Pixel 7) that transcribes long rambling speech on the device into a
timestamped Markdown session file, answers short spoken questions about the session from a small local LLM, and exports
the raw session for desktop processing. This is a proof of concept. Its purpose is to produce go/no-go evidence
(`docs/gates.md`), not a polished app. Status: planning. Nothing has been measured on the phone yet.

## Source of truth (read in this order)

1. `docs/requirements.md` - requirement IDs (`REQ-*`)
2. `docs/gates.md` - pass/fail thresholds (`GATE-*`) and the rules for recording results
3. `docs/plan/steps.md` - work items (`STEP-*`) and who can do them
4. `docs/adr/` - accepted architecture decisions
5. `docs/decisions-log.md` - research decisions (`DEC-*`) with their evidence status
6. `docs/plan/project-plan.md` - overview, schedule, risks

If two documents disagree, the higher one wins. Fix the lower one in the same change.

## Commands

Python tools (run from `tools/`):

```bash
pip install -e ".[dev]"
ruff check . && ruff format --check . && mypy src && pytest
```

Agent definitions (from the repository root; CI runs the check once STEP-40 is done):

```bash
python -m voicetools sync-agents --check   # use without --check to regenerate
```

Kotlin core, pure JVM, no Android SDK needed to run the tests (run from `android/`; needs the Gradle wrapper, see README):

```bash
./gradlew :core:test
```

App (needs Android SDK and NDK): `./gradlew :app:assembleDebug`.

Pre-commit hooks (from the repository root; config in `.pre-commit-config.yaml`). They run on every commit once
installed: file hygiene, ruff, mypy, the agent sync check, and a block on `data/`, `models/` and audio files. They do
not replace the test commands above. Never bypass them with `--no-verify`; fix the cause or ask.

```bash
pre-commit install          # once per clone
pre-commit run --all-files  # run every hook by hand
```

Run every check that applies to your change before you finish.

## Layout

- `android/core/` - pure Kotlin/JVM module: session format, domain types, interfaces. No Android imports. No coroutines.
- `android/app/` - Android app: UI, foreground service, Android-specific implementations.
- `tools/` - Python desktop tooling (`voicetools`): session parser and linter, planted-fact scorer.
- `fixtures/` - shared test inputs used by BOTH the Kotlin and Python tests. Treat as read-only ground truth.
- `agents/` - source of the role-specific agent definitions (generated into `.claude/agents/` and `.github/agents/`).
- `docs/` - requirements, gates, plan, ADRs, test protocol, results log.
- `data/` and `models/` - git-ignored. Recordings and model files live here. Never commit them.

## Agent roles

Role definitions live in `agents/*.md` and are generated into `.claude/agents/` (Claude Code) and `.github/agents/`
(Copilot). Edit only `agents/`, then run `python -m voicetools sync-agents`. Never edit the generated files.

Roles: `session-format-guardian`, `kotlin-core-engineer`, `python-tools-engineer`, `android-app-engineer`,
`results-scribe`, `reviewer` (read-only), `doc-auditor` (read-only). Run `reviewer` on every change before a human looks
at it. An agent must not review its own work.

## How to work

1. Pick exactly one `STEP-*` from `docs/plan/steps.md`. Read its "Who" column first:
   `A` = you can finish it alone, `S` = needs the Android SDK/NDK on this machine,
   `D` = needs the physical Pixel 7, `H` = needs a human decision or action.
2. If a step needs `D` or `H`, do the parts you can (code, scripts, checklists), then stop and hand over with exact
   instructions for what the human must run and which numbers to paste into `docs/results/RESULTS-LOG.md`.
3. Write or update the test first, then the code. Keep each change small and reviewable.
4. Reference IDs (`STEP-*`, `REQ-*`, `GATE-*`, `DEC-*`) in commit messages, test names and code comments.
5. Stop at every review checkpoint listed in `docs/plan/project-plan.md` section 11 and wait for a human.

## Boundaries

Always:
- Keep `android/core` free of Android imports and free of third-party runtime dependencies.
- Keep the capture path independent of the LLM and TTS: capture never waits on them.
- Write raw audio first; transcription may lag but audio must never be dropped.
- Label anything you could not verify as `UNVERIFIED` and say where the claim came from.

Ask a human first:
- Adding a dependency, library or model (needs an ADR; check its license).
- Changing the session file format (bump `format=` and update both parsers and `fixtures/`).
- Changing a public interface in `android/core`.
- Changing any threshold in `docs/gates.md` or any success criterion.

Never:
- Stage or commit anything (`git add`, `git commit`, `git stash`, `git commit --amend`, or any equivalent) unless a
  human explicitly asks for it. Finish with the changes in the working tree and say what you would commit. Read-only
  git commands (`status`, `diff`, `log`, `show`) are always fine.
- Push, force-push, delete a remote branch or open a pull request unless a human explicitly asks for that exact action.
  Pushing is not blocked by `.claude/settings.json`, so this rule is the only guard. Approval for one push does not carry
  over to the next.
- Commit audio, models, secrets, API keys or personal data. `data/` and `models/` are ignored on purpose.
- Add any network call to the app's core path (requirement `REQ-NFR-01`: works in airplane mode).
- Invent a measurement, benchmark number or library API. If you did not run it or read it, say so.
- Mark a gate PASS yourself. Gates are decided by a human from measured numbers in the results log.
- Edit `fixtures/` or `docs/testing/` ground truth to make a test pass. Fix the code, or ask.
- Log transcript text to shared logs or exports unless the user asked for it.
- Hand-edit files under `.claude/agents/` or `.github/agents/` (they are generated from `agents/`).

## Conventions

### Python (`tools/`)

- Python 3.10+, full type hints, `mypy --strict` clean, `ruff` clean.
- Frozen dataclasses with behavior on the object (methods), not anemic data bags with logic in free functions.
- Alternate constructors as classmethods (`Entry.speech(...)`), not module-level factory functions.
- Keyword-only arguments for anything with more than two parameters.
- `pathlib.Path` at API boundaries, never bare `str` paths.
- No dependencies at runtime unless an ADR says otherwise (dev tools only: pytest, ruff, mypy, pre-commit).

### Kotlin (`android/`)

- Idiomatic Kotlin: immutable `data class`, `sealed interface` for closed hierarchies, no `!!`.
- Interfaces in `core` describe capabilities; Android and native implementations live in `app`.
- One owner per native handle (speech engine, LLM context). Guard native state with a mutex. Always unload.
- Compose for UI; the foreground service owns the pipeline, the UI only observes it.
- Package name is `org.martindemling.cedarcapture` (renamed from the placeholder `org.example.voicecapture`).
  `tools/tests/test_android_package.py` keeps namespace, `package` lines and directories in agreement.

### Docs and IDs

- Never reuse or renumber an ID. Add new ones at the end.
- Refer to entries by ID, not by list position.
- Numbers in docs come from `docs/results/RESULTS-LOG.md` or a cited source, never from memory.

### Commits and pull requests

- Conventional Commits with the ID as scope or suffix: `feat(core): parse session files (STEP-03)`.
- Branch names: `step-03-session-format`. One step per branch where possible.
- Merge locally with a merge commit (`git merge --no-ff <branch>`), never squash or rebase, and only when asked.
  Pull requests are not used yet (open decision 8 in `docs/plan/project-plan.md`: CI arrives with STEP-40). When they
  are, fill in `.github/pull_request_template.md` completely.
- Work on a feature branch, not on `master`; the default branch is `master`.

## Definition of done (every task)

1. Tests pass locally (commands above), including new tests for new behavior.
2. No new warnings from `ruff`, `mypy`, or the Kotlin compiler.
3. Docs updated where behavior or IDs changed. `CHANGELOG.md` updated for user-visible changes.
4. Results log updated if you produced or received measurements.
5. A short hand-off note: what changed, what you could not verify, what a human must do next.
