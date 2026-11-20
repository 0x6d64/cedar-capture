# android/ agent notes

- `core/` is pure Kotlin/JVM: no Android imports, no third-party runtime dependencies, no coroutines (ADR-0002).
  Run its tests with `./gradlew :core:test` (after the wrapper exists, see root README).
- `app/` was never built. Gradle files, dependency versions and the app sources are UNVERIFIED placeholders: STEP-01
  verifies and bumps versions in `gradle/libs.versions.toml` first. Do not assume any API call here compiles.
- The foreground service owns the pipeline; the UI only observes. Capture never waits on the LLM or TTS.
- One owner per native handle; guard native state with a mutex; always unload.
- No INTERNET permission without an ADR (REQ-NFR-01).
- The Kotlin session reader/writer mirrors `tools/src/voicetools/session.py`; both are checked against `fixtures/`.
