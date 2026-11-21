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

### STEP-01 (re-check after package rename) - 2026-11-21

- Label: n/a (not a gate)    (human-confirmed: no)
- Device / Android version / patch level: Pixel 7 (`panther`), serial `2C201FDH2005HA`; Android 17 / API 37.1, patch
  2026-10-05 as in the GATE-01 entry below (not re-read in this run)
- App version (git commit): `dca2af6` (package rename completed), debug build, `versionName 0.1.0`; APK built from that
  tree in the same session
- Tool versions (engine, runtime, NDK, Gradle): AGP 9.5.2, Gradle 9.6.0, Kotlin 2.2.10, JDK 17 (Temurin 17.0.21)
- Model file(s), quantization, source and license: n/a
- Recording used (name, run a/b/c): n/a
- Raw numbers (with units):
  - `./gradlew clean :core:test :app:assembleDebug` -> `BUILD SUCCESSFUL`; core test results XML: 11 tests, 0 failures,
    0 errors; `app-debug.apk` 29,010,957 bytes
  - `adb install -r app-debug.apk` -> `Performing Streamed Install` / `Success` (a first attempt hung and was stopped;
    the retry with a 90 s timeout succeeded, cause of the hang not determined)
  - `adb shell pm list packages` -> `package:org.martindemling.cedarcapture`
  - `adb shell monkey -p org.martindemling.cedarcapture -c android.intent.category.LAUNCHER 1` -> `Events injected: 1`
  - `adb shell dumpsys activity activities` 4 s after launch -> `topResumedActivity=ActivityRecord{... org.martindemling.cedarcapture/.MainActivity ...}`
  - `adb logcat -d -b crash` after `logcat -c` and launch -> empty; `adb shell pidof` -> `32455` (process alive)
- Thresholds compared against (docs/gates.md revision): STEP-01 exit check in `docs/plan/steps.md`: "app installs and
  starts on the Pixel 7" -> met again after the rename
- Notes, anomalies, what was not verified: purpose is to confirm that the manifest's relative names (`.MainActivity`,
  `.CaptureService`) still resolve after the namespace and `package` lines were aligned (commit `dca2af6`). No
  screenshot was taken, so the on-screen title and buttons were not re-observed. **Start recording** and **Stop** were
  not tapped; the known `SecurityException` on Start (missing runtime `RECORD_AUDIO`, STEP-06) is therefore not
  re-confirmed. Agent-run check; a human has not confirmed it.

### STEP-01 (on-device exit check) - 2026-11-21

- Label: n/a (not a gate)    (human-confirmed: no)
- Device / Android version / patch level: Pixel 7 (`panther`), serial `2C201FDH2005HA`, Android 17, API 37
  (`ro.build.version.sdk_full` = 37.1), security patch 2026-10-05, ABI arm64-v8a. Same run as the GATE-01 entry below.
- App version (git commit): commit `a55f782` (toolchain pin), unmerged working tree; `versionName 0.1.0`, debug build
- Tool versions (engine, runtime, NDK, Gradle): as in the build entry below (AGP 9.5.2, Gradle 9.6.0, Kotlin 2.2.10)
- Model file(s), quantization, source and license: n/a
- Recording used (name, run a/b/c): n/a
- Raw numbers (with units):
  - `adb devices -l` -> `2C201FDH2005HA device product:panther model:Pixel_7 device:panther transport_id:1`
  - `adb install -r app-debug.apk` -> `Performing Streamed Install` / `Success`
  - `adb shell monkey -p org.martindemling.cedarcapture -c android.intent.category.LAUNCHER 1` -> `Events injected: 1`
  - `adb shell dumpsys activity activities` -> `topResumedActivity=ActivityRecord{... org.martindemling.cedarcapture/.MainActivity ...}`
  - screenshot taken 4 s after launch: renders "Voice Capture - skeleton" with "Start recording" and "Stop" buttons,
    no system dialog, status bar clock 12:25, battery 53%
  - `adb logcat -d --pid 29142` -> Compose starts, `ProfileInstaller: Installing profile`; no warnings or errors
  - `adb logcat -d -b crash` filtered for `voicecapture` -> no entries (the only crash-buffer lines predate this run)
- Thresholds compared against (docs/gates.md revision): STEP-01 exit check in `docs/plan/steps.md`: "app installs and
  starts on the Pixel 7" -> met
- Notes, anomalies, what was not verified: launch is proven, behaviour is not benign. Tapping **Start recording**
  crashed the app: `java.lang.SecurityException: Starting FGS with type microphone ... requires permissions: all of the
  permissions [android.permission.FOREGROUND_SERVICE_MICROPHONE] and any of the permissions [... RECORD_AUDIO]`, thrown
  from `CaptureService.onStartCommand(CaptureService.kt:25)` -> `Service.startForeground`. Cause: the manifest declares
  `RECORD_AUDIO` but nothing ever requests it at runtime, and STEP-06 is the step that adds that flow. This is the
  documented skeleton gap, not a regression. Two further crashes of the same kind are logged at 12:29:02 and 12:29:04;
  who pressed what then is not recorded. **Stop** was not exercised. STEP-01 is `done` for its exit check.

### GATE-01 - 2026-11-21

- Label: n/a (fact, `docs/gates.md` defines no threshold)    (human-confirmed: no)
- Device / Android version / patch level: Pixel 7 (`panther`), Android **17**, API level **37**
  (`ro.build.version.sdk` = 37, `ro.build.version.sdk_full` = **37.1**), security patch **2026-10-05**,
  build codename `REL`, ABI `arm64-v8a`
- App version (git commit): n/a (device property read, no app involved)
- Tool versions (engine, runtime, NDK, Gradle): n/a
- Model file(s), quantization, source and license: n/a
- Recording used (name, run a/b/c): n/a
- Raw numbers (with units): `adb shell getprop ro.build.version.release` -> `17`;
  `ro.build.version.sdk` -> `37`; `ro.build.version.sdk_full` -> `37.1`;
  `ro.build.version.security_patch` -> `2026-10-05`; `ro.product.cpu.abi` -> `arm64-v8a`
- Thresholds compared against (docs/gates.md revision): GATE-01 is a fact to record, no threshold
- Notes, anomalies, what was not verified: the phone runs the 37.1 minor API level, so `compileSdk 37.1` and with it
  Compose BOM 2026.11.01 are now realistic (platform `android-37.0` is what is installed on the build machine today;
  37.1 would have to be installed). `minSdk` is still the placeholder 33 and was not changed: the app installs and runs
  on API 37, and lowering the floor costs nothing for a POC. Whether REQ-NFR-* work on this exact build is untouched.

### STEP-01 (build, no device) - 2026-11-21

- Label: n/a (toolchain pinning, not a gate)    (human-confirmed: no)
- Device / Android version / patch level: NOT MEASURED at build time (no device attached: `adb devices` empty). The
  phone was attached later the same day; see the on-device entry above.
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
  - `adb devices` -> no devices attached at build time. The install and launch check is the on-device entry above.
- Thresholds compared against (docs/gates.md revision): n/a
- Notes, anomalies, what was not verified: first `:app:assembleDebug` failed with `checkDebugAarMetadata`: Compose
  1.13.0 (BOM 2026.11.01) requires `compileSdk 37.1`, only 37.0 is installed; pinned BOM 2026.09.00 instead. Build emits
  `[CXX1101]` because `ndk/30.0.16248370` has no `source.properties` (partial install), and `libandroidx.graphics.path.so`
  is not stripped. NDK support must be re-checked at STEP-11. `kotlin-android` was removed for AGP 9 built-in Kotlin.
