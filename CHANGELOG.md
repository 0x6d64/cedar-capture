# Changelog

All notable changes to this project are documented here. Format: Keep a Changelog. Versioning: 0.x until the go/no-go decision.

## [Unreleased]

### Changed
- Android toolchain pinned and proven to build and run (STEP-01): AGP 9.5.2, Gradle 9.6.0 (wrapper now committed),
  Kotlin 2.2.10, compileSdk 37, Compose BOM 2026.09.00, JDK 17. `kotlin-android` removed in favour of AGP 9 built-in
  Kotlin. `:core:test` (11 tests) and `:app:assembleDebug` both pass, and the APK installs and starts on the Pixel 7.
  Sources, raw numbers and caveats: `docs/pinned-versions.md` and `docs/results/RESULTS-LOG.md`.

### Added
- Pre-commit hooks (`.pre-commit-config.yaml`, revisions pinned to commit SHAs): file hygiene, ruff check and format,
  mypy, the `sync-agents --check` drift check, and a block on committing `data/`, `models/` or audio files.
  `fixtures/` is excluded from the whitespace fixers because it is ground truth.
- Capture pipeline with fakes (STEP-05): `CapturePipeline` runs source, raw audio, voice detection, a recognition queue
  and the session store, with a `PipelineState` (Idle, Recording, Stopped, Error). Raw audio is written before the
  detector sees a frame, and capture never waits for recognition. A recognizer failure is reported and capture goes on; a
  source, raw-sink or store failure ends in `Error`. New `RawAudioSink` interface (public `core` API, approved in the
  STEP-03 to STEP-05 plan). A generated WAV through the pipeline gives the expected session file.
- WAV replay `AudioSource` (STEP-04): `WavAudioSource` reads 16 kHz mono PCM16 WAV into the caller's buffer (the pipeline
  will use 512-sample frames), fast or in real time (`Pacing`, testable through `PacingClock`), and rejects other
  formats, including WAVE_FORMAT_EXTENSIBLE, with a clear message. `VoiceActivityDetector`
  gains `flush()` so the last words are not lost when a source ends (public `core` API change approved in the STEP-03
  to STEP-05 plan). Test fakes `EnergyVad`, `FakeRecognizer` and the in-memory `TestWav` generator.
- Session recovery (STEP-03): `SessionFormat.recover` (Kotlin) and `Recovery.from_text` (Python) cut an unfinished
  session file back to its last whole entry and build the `[INTERRUPTED]` marker; spec writing rule 2 now says how.
  Format stays version 1. New tests, including recovery from every prefix of a valid file, in both languages. The new
  public `core` API (`Recovery`, `recover`) was approved by the human in the STEP-03 to STEP-05 plan (2026-11-21).
- Role-specific agent definitions in `agents/`, generated for Claude Code and Copilot (`voicetools sync-agents`).
- Initial repository skeleton: docs, test protocol, session format spec, Kotlin core module, Python tooling, CI.
