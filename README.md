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
2. The Kotlin `core` module compiles and its 11 tests pass through Gradle 9.6.0 / JUnit
   (2026-11-21: `SessionFormatTest`, 11 tests, 0 failures) against the shared fixtures.
3. The Android `app` module builds and runs: `./gradlew :app:assembleDebug` produced a 29 MB debug APK on 2026-11-21,
   and that APK installed and started on the Pixel 7 (title "Voice Capture - skeleton", two buttons, no crash). Tapping
   **Start recording** crashes with `SecurityException: Starting FGS with type microphone ... requires ... RECORD_AUDIO`,
   because the skeleton never requests the runtime permission; STEP-06 adds that flow. So the app is a shell at this
   stage: no audio is captured, nothing is transcribed, no file is written. The versions in
   `android/gradle/libs.versions.toml` were checked against vendor documentation (AGP 9.5.2, Gradle 9.6.0, Kotlin
   2.2.10, Compose BOM 2026.09.00); sources, measured results and open questions: `docs/pinned-versions.md`. The CI
   workflow has still not run. The Claude Code and Copilot configuration files (including the generated agent files)
   follow conventions from memory and were not checked against current documentation. Details:
   `docs/skeleton-status.md`.
4. The Gradle wrapper is committed (`gradlew`, `gradlew.bat`, `gradle/wrapper/*`, Gradle 9.6.0). You need JDK 17 and an
   Android SDK with platform 37.0; set `JAVA_HOME` and `ANDROID_HOME` before building. The app runs on the Pixel 7 with
   Android 17 / API 37.1, patch 2026-10-05 (GATE-01, in the results log).

## Bootstrap

```bash
# Python tools
cd tools && python -m venv .venv && . .venv/bin/activate
pip install -e ".[dev]"
ruff check . && ruff format --check . && mypy src && pytest

# Android (needs JDK 17 and an SDK with platform 37.0; set JAVA_HOME and ANDROID_HOME)
cd ../android
./gradlew :core:test          # 11 tests
./gradlew :app:assembleDebug  # APK in app/build/outputs/apk/debug/

# On the phone (adb ships with the SDK but is not always on PATH;
# on Windows use "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe")
adb devices -l
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell monkey -p org.example.voicecapture -c android.intent.category.LAUNCHER 1
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
