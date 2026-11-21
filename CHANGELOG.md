# Changelog

All notable changes to this project are documented here. Format: Keep a Changelog. Versioning: 0.x until the go/no-go decision.

## [Unreleased]

### Changed
- Android toolchain pinned and proven to build and run (STEP-01): AGP 9.5.2, Gradle 9.6.0 (wrapper now committed),
  Kotlin 2.2.10, compileSdk 37, Compose BOM 2026.09.00, JDK 17. `kotlin-android` removed in favour of AGP 9 built-in
  Kotlin. `:core:test` (11 tests) and `:app:assembleDebug` both pass, and the APK installs and starts on the Pixel 7.
  Sources, raw numbers and caveats: `docs/pinned-versions.md` and `docs/results/RESULTS-LOG.md`.

### Added
- Session recovery (STEP-03): `SessionFormat.recover` (Kotlin) and `Recovery.from_text` (Python) cut an unfinished
  session file back to its last whole entry and build the `[INTERRUPTED]` marker; spec writing rule 2 now says how.
  Format stays version 1. New tests, including recovery from every prefix of a valid file, in both languages.
- Role-specific agent definitions in `agents/`, generated for Claude Code and Copilot (`voicetools sync-agents`).
- Initial repository skeleton: docs, test protocol, session format spec, Kotlin core module, Python tooling, CI.
