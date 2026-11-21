# Results log

Every measurement goes here, newest first. Agents may draft entries from numbers a human pasted, but must write
`NOT MEASURED` for anything missing and must mark any label as `PROPOSED`. A human confirms the label.

## Entry template (copy this block)

```
### <GATE-ID or STEP-ID> - <YYYY-MM-DD>

- Label: PROPOSED PASS | MARGINAL | FAIL | n/a    (human-confirmed: yes/no)
- Device / Android version / patch level:
- App version (git commit):
- Tool versions (engine, runtime, NDK, Gradle):
- Model file(s), quantization, source and license:
- Recording used (name, run a/b/c):
- Raw numbers (with units):
- Thresholds compared against (docs/gates.md revision):
- Notes, anomalies, what was not verified:
```

## Entries

### STEP-01 - 2026-11-21

- Label: n/a (toolchain pinning, not a gate)    (human-confirmed: no)
- Device / Android version / patch level: NOT MEASURED (no device attached: `adb devices` empty, GATE-01 open)
- App version (git commit): UNCOMMITTED working tree
- Tool versions (engine, runtime, NDK, Gradle): AGP 9.5.2, Gradle 9.6.0 (wrapper committed), Kotlin/KGP 2.2.10,
  Compose BOM 2026.09.00 (UI 1.12.1), JDK 17.0.21.1+2 (Temurin), Build Tools 36.0.0, NDK 30.0.16248370 (incomplete
  install), CMake 4.1.2, compileSdk 37, targetSdk 36, minSdk 33. Sources and how each value was checked:
  `docs/pinned-versions.md`.
- Model file(s), quantization, source and license: n/a
- Recording used (name, run a/b/c): n/a
- Raw numbers (with units):
  - `./gradlew :core:test` -> BUILD SUCCESSFUL in 25 s; `SessionFormatTest` 11 tests, 0 skipped, 0 failures, 0 errors,
    test time 0.092 s.
  - `./gradlew :app:assembleDebug` -> BUILD SUCCESSFUL in 23 s; `app-debug.apk` 29,010,949 bytes.
  - `gradle wrapper --gradle-version 9.6.0` -> BUILD SUCCESSFUL in 44 s.
  - `adb devices` -> no devices attached, so the app was not installed or started. STEP-01 therefore stays open.
- Thresholds compared against (docs/gates.md revision): n/a
- Notes, anomalies, what was not verified: first `:app:assembleDebug` failed with `checkDebugAarMetadata`: Compose
  1.13.0 (BOM 2026.11.01) requires `compileSdk 37.1`, only 37.0 is installed; pinned BOM 2026.09.00 instead. Build emits
  `[CXX1101]` because `ndk/30.0.16248370` has no `source.properties` (partial install), and `libandroidx.graphics.path.so`
  is not stripped. NDK support must be re-checked at STEP-11. `kotlin-android` was removed for AGP 9 built-in Kotlin.
