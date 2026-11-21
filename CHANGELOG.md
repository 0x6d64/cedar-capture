# Changelog

All notable changes to this project are documented here. Format: Keep a Changelog. Versioning: 0.x until the go/no-go decision.

## [Unreleased]

### Changed
- Android toolchain pinned and proven to build (STEP-01): AGP 9.5.2, Gradle 9.6.0 (wrapper now committed),
  Kotlin 2.2.10, compileSdk 37, Compose BOM 2026.09.00, JDK 17. `kotlin-android` removed in favour of AGP 9 built-in
  Kotlin. `:core:test` (11 tests) and `:app:assembleDebug` both pass; the app was not installed on the phone yet.
  Sources, raw numbers and caveats: `docs/pinned-versions.md` and `docs/results/RESULTS-LOG.md`.

### Added
- Role-specific agent definitions in `agents/`, generated for Claude Code and Copilot (`voicetools sync-agents`).
- Initial repository skeleton: docs, test protocol, session format spec, Kotlin core module, Python tooling, CI.
