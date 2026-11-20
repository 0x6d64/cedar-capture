# offline-voice-capture

Proof of concept: an offline Android app (Kotlin, Google Pixel 7) that transcribes long rambling speech on the device
into a timestamped Markdown session, answers short spoken questions about it from a small local LLM, and exports the raw
session for processing on a desktop.

**Status: planning.** Nothing has been built or measured on the phone. The goal of this repository is to produce
go/no-go evidence (see `docs/gates.md`) before any larger investment.

## What is in the box

| Path | Purpose |
| --- | --- |
| `AGENTS.md`, `CLAUDE.md`, `.github/copilot-instructions.md` | Instructions for AI coding agents |
| `agents/` | Source of the role-specific agent definitions (generated for Claude Code and Copilot) |
| `docs/` | Requirements, gates, plan, steps, decisions, test protocol, results log |
| `android/core/` | Pure Kotlin/JVM module: session format, domain types, interfaces (has tests) |
| `android/app/` | Android app skeleton: manifest, Compose activity, foreground service stub |
| `tools/` | Python tooling: session parser/linter, planted-fact scorer (has tests) |
| `fixtures/` | Shared test inputs used by both Kotlin and Python tests |
| `data/`, `models/` | Git-ignored: your recordings and model files |

## What is verified and what is not

1. The Python tooling was checked in a clean virtual environment: `ruff`, `mypy --strict` and 37 `pytest` tests pass (including a check that the generated agent files match `agents/`).
2. The Kotlin `core` module was compiled with the Kotlin compiler and its 11 tests were run against the shared fixtures
   using a local stand-in for `kotlin.test`. They have NOT been run through Gradle and JUnit.
3. The Android `app` module, the Gradle files, the CI workflow and the dependency versions were NOT built or run.
   Version numbers in `android/gradle/libs.versions.toml` are placeholders: STEP-01 starts by verifying and bumping them.
   The Claude Code and Copilot configuration files (including the generated agent files) follow conventions from memory and were not checked against current
   documentation. Details: `docs/skeleton-status.md`.
4. There is no Gradle wrapper yet (it contains a binary jar). Generate it once, see below.

## Bootstrap

```bash
# Python tools
cd tools && python -m venv .venv && . .venv/bin/activate
pip install -e ".[dev]"
ruff check . && ruff format --check . && mypy src && pytest

# Android (needs JDK 17 and Gradle installed once, to create the wrapper)
cd ../android
gradle wrapper --gradle-version 8.11.1      # then commit gradlew, gradlew.bat, gradle/wrapper/*
./gradlew :core:test
```

Licensed under the GNU General Public License v3.0; see `LICENSE`. Check the licenses of any model you add to
`models/` before committing or sharing it.

## Working with coding agents

Read `docs/agent-workflow.md`. In short: an agent picks one `STEP-*` from `docs/plan/steps.md`, does the parts that need
no phone, and hands over the parts that do. Humans decide every gate.

## Where to start

1. `docs/plan/project-plan.md` - overview and schedule
2. `docs/plan/steps.md` - the work items
3. `docs/testing/test-protocol.md` - the recording you need to make first
