# android/ agent notes

- `core/` is pure Kotlin/JVM: no Android imports, no third-party runtime dependencies, no coroutines (ADR-0002).
  Run its tests with `./gradlew :core:test`. The wrapper (Gradle 9.6.0) is committed.
- `app/` compiles and packages as of 2026-11-21 (`./gradlew :app:assembleDebug`), but has never run on the phone, so
  treat its runtime behaviour as UNVERIFIED. Toolchain values and their sources are pinned in `docs/pinned-versions.md`;
  do not bump one without re-reading the compatibility tables.
- The foreground service owns the pipeline; the UI only observes. Capture never waits on the LLM or TTS.
- One owner per native handle; guard native state with a mutex; always unload.
- No INTERNET permission without an ADR (REQ-NFR-01).
- The Kotlin session reader/writer mirrors `tools/src/voicetools/session.py`; both are checked against `fixtures/`.
