# Pinned versions

One place for every tool, library and model version this project builds with (project-plan section 11.3).
A value is only `VERIFIED` if the source page was opened and read. `SNIPPET` means it came from a search result and
was not confirmed.

Last updated: 2026-11-21 (STEP-01 and GATE-01). `./gradlew :app:assembleDebug` and `./gradlew :core:test` both ran green
on this machine, and the APK was installed and started on the Pixel 7 the same day.

## Build toolchain

| Component | Pinned value | Source | How checked | State |
| --- | --- | --- | --- | --- |
| Android Gradle Plugin | 9.5.2 | [AGP 9.5 release notes](https://developer.android.com/build/releases/gradle-plugin) and [Google Maven metadata](https://dl.google.com/dl/android/maven2/com/android/tools/build/gradle/maven-metadata.xml) | release notes opened; 9.5.2 is the newest non-alpha in the metadata | VERIFIED, BUILT OK |
| Gradle | 9.6.0 | AGP 9.5 notes (minimum = default = 9.6.0); [distribution zip](https://services.gradle.org/distributions/gradle-9.6.0-bin.zip) returns HTTP 200; [wrapper-to-AGP table](https://developer.android.com/build/releases/about-agp) agrees | pages opened; wrapper generated and used for both builds | VERIFIED, BUILT OK |
| Kotlin / KGP | 2.2.10 | `com.android.tools.build:gradle:9.5.2` POM lists `org.jetbrains.kotlin:kotlin-gradle-plugin:2.2.10`; [AGP 9.0 notes](https://developer.android.com/build/releases/agp-9-0-0-release-notes) give 2.2.10 as the built-in-Kotlin floor | POM fetched and decoded; notes opened | VERIFIED, BUILT OK |
| Compose Compiler plugin | 2.2.10 (same as Kotlin) | [Compose setup guide](https://developer.android.com/develop/ui/compose/setup-compose-dependencies-and-compiler): "this version matches your Kotlin version" | page opened; plugin marker resolved (HTTP 200) | VERIFIED, BUILT OK |
| JDK (Gradle runtime and `core` toolchain) | 17 | AGP 9.5 notes (minimum = default = 17); `.github/workflows/ci.yml` uses 17 | installed 2026-11-21: Eclipse Temurin 17.0.21.1+2 | VERIFIED, BUILT OK |
| SDK Build Tools | 36.0.0 | AGP 9.5 notes (minimum = default) | notes opened; installed | VERIFIED, BUILT OK |
| NDK | 30.0.16248370 (explicit `ndkVersion`) | Installed on the machine; AGP 9.5 defaults to 28.2.13676358 | build warns `[CXX1101] NDK at ...\ndk\30.0.16248370 did not have a source.properties file` | **BROKEN INSTALL**: the folder holds only `toolchains/`, `wrap.sh` and `package.xml`. Reinstall through the SDK Manager before STEP-11, or switch to 28.2.13676358 |
| CMake | 4.1.2 | Installed on the machine (needed for STEP-11/STEP-21) | SDK listing | Installed. NOT RUN |
| compileSdk | 37 | [Compose setup guide](https://developer.android.com/develop/ui/compose/setup-compose-dependencies-and-compiler): Compose 1.12.0+ requires `compileSdk 37` and AGP 9; [API-level table](https://developer.android.com/build/releases/about-agp) needs AGP 9.1.1 for API 37.0 | both pages opened; platform `android-37.0` installed; build passed; GATE-01 shows the phone reports `sdk_full` 37.1, so a 37.1 platform would also be valid if installed | VERIFIED, BUILT OK |
| targetSdk | 36 | Placeholder from the skeleton, unchanged | n/a | PROPOSED. The claim that Play requires 36 since 31 Aug 2026 came from third-party snippets and was NOT confirmed; irrelevant for sideloading. GATE-01 came back as API 37.1, so 36 is below the device; nothing forces a bump for a sideloaded POC |
| minSdk | 33 | Placeholder from the skeleton | n/a | PROPOSED. GATE-01 answered the question it was waiting on (device = API 37.1); 33 was kept on purpose because the APK installs and runs there and a lower floor costs nothing |

## Libraries

| Component | Pinned value | Source | How checked | State |
| --- | --- | --- | --- | --- |
| Compose BOM | 2026.09.00 (Compose UI 1.12.1) | [Google Maven metadata](https://dl.google.com/dl/android/maven2/androidx/compose/compose-bom/maven-metadata.xml); AAR metadata `minCompileSdk` read from the published artifacts | 1.13.0 needs `compileSdk 37.1` (not installed); 1.12.1 needs `compileSdk 37`, which is installed | VERIFIED, BUILT OK |
| androidx.core:core-ktx | 1.19.1 | [Google Maven metadata](https://dl.google.com/dl/android/maven2/androidx/core/core-ktx/maven-metadata.xml) | metadata fetched; newest stable (1.20.0-alpha01 is a prerelease) | VERIFIED, BUILT OK |
| androidx.activity:activity-compose | 1.13.0 | [Google Maven metadata](https://dl.google.com/dl/android/maven2/androidx/activity/activity-compose/maven-metadata.xml) | metadata fetched; newest stable; also the version in the Compose setup guide | VERIFIED, BUILT OK |

## Measured build and device results (2026-11-21, Windows 10)

| Command | Result |
| --- | --- |
| `gradle wrapper --gradle-version 9.6.0` (Gradle 9.6.0, JDK 17) | BUILD SUCCESSFUL in 44 s; `gradlew`, `gradlew.bat`, `gradle-wrapper.jar` generated |
| `./gradlew :core:test` | BUILD SUCCESSFUL in 25 s; `SessionFormatTest`: 11 tests, 0 failures, 0 errors (0.092 s) |
| `./gradlew :app:assembleDebug` | BUILD SUCCESSFUL in 23 s; `app/build/outputs/apk/debug/app-debug.apk`, 29,010,949 bytes |
| `adb devices -l` (later the same day) | `2C201FDH2005HA device product:panther model:Pixel_7 ...` |
| `adb install -r app-debug.apk` | `Performing Streamed Install` / `Success` |
| launch + `dumpsys activity activities` | `topResumedActivity=... org.martindemling.cedarcapture/.MainActivity`; screenshot shows "Voice Capture - skeleton" with two buttons; no crash in `adb logcat -b crash` |

Warnings seen, both benign for now: `[CXX1101]` about the broken NDK install, and `libandroidx.graphics.path.so` not
stripped because no usable NDK toolchain was found.

## Device (measured 2026-11-21, GATE-01)

| Item | Value |
| --- | --- |
| Model | Pixel 7 (`panther`), serial `2C201FDH2005HA` |
| Android | 17 |
| API level | `ro.build.version.sdk` = 37, `ro.build.version.sdk_full` = 37.1 |
| Security patch | 2026-10-05 |
| ABI | arm64-v8a |

## Build machine

| Item | Value |
| --- | --- |
| Android Studio | installed, bundled JBR OpenJDK 25.0.4 (not used for the build) |
| JDK used | Eclipse Temurin 17.0.21.1+2, `C:\Program Files\Eclipse Adoptium\jdk-17.0.21.10002-hotspot` |
| Gradle on PATH | none. The wrapper (9.6.0) drives the build; the distribution is downloaded into `~/.gradle/wrapper/dists` |
| adb | 1.0.41 / 37.0.3-16887417 |
| SDK platforms | android-37.0 |
| Build Tools | 36.0.0 |
| NDK | 30.0.16248370, incomplete (see above) |
| CMake | 4.1.2 |
| `java` on PATH | Oracle JRE 1.8.0_191 — still unusable; the build needs `JAVA_HOME` pointing at JDK 17 |

## Rejected alternatives

- **AGP 9.2.0 with Gradle 9.4.1** (the previous draft of this decision): two minor releases behind; AGP 9.4 and 9.5
  both require Gradle 9.6.0, and 9.2.0 has a fixed bug (ClassNotFoundException `com.android.tools.r8.RecordTag`) that
  9.5.x does not have.
- **Gradle 9.9.2** (the newest release, published 2026-11-19): allowed with AGP 9.5, but a one-day-old distribution buys
  nothing here. AGP's own default, 9.6.0, is the combination Google tests. Revisit only if 9.6.0 misbehaves.
- **Kotlin 2.4.21** (newest stable KGP): above what AGP 9.5.2 ships. Allowed through the documented buildscript-classpath
  upgrade, but it adds a moving part for no gain: Compose UI 1.12.1 also sits on the 2.2 Kotlin line.
- **Studio's bundled JDK 25 as the Gradle runtime**: Gradle 9.6.0 does support Java 25, but AGP 9.5 documents 17 and CI
  uses 17. One JDK for both the runtime and the toolchain keeps bytecode and CI identical.
- **Compose BOM 2026.11.01** (newest, Compose UI 1.13.0): its AAR metadata demands `compileSdk 37.1`; only platform
  37.0 is installed and AGP 9.5's documented API-level table stops at 37.0. Downgrading one BOM step was cheaper and
  safer than chasing a minor API level. Revisit when 37.1 is installed.
- **AGP 8.9.1 / Gradle 8.11.1 / Kotlin 2.1.0** (the original placeholders): below AGP 9 minimums, and the
  `kotlin-android` plugin is incompatible with the AGP 9 DSL.

## Consequences written into the build files

- `kotlin-android` was removed from `libs.versions.toml`, the root build file and `app/build.gradle.kts`: AGP 9 uses
  built-in Kotlin and rejects that plugin.
- `app` no longer sets `kotlin { jvmToolchain(17) }`; with built-in Kotlin the Kotlin jvmTarget follows
  `compileOptions.targetCompatibility` (17). `core` keeps `jvmToolchain(17)` because it uses the plain Kotlin/JVM plugin.
- `compileSdk` went from 36 to 37 so the pinned Compose line is allowed.

## Still not verified

1. Anything the app does beyond showing its first screen. **Start recording** is proven to crash (missing runtime
   `RECORD_AUDIO`, STEP-06); **Stop** is unexercised; no audio, transcript or session file exists in this build.
2. Whether AGP 9.5 accepts this NDK once native code arrives (STEP-11). The current r30 folder is incomplete; repair it
   or install the AGP default 28.2.13676358.
3. Whether the CI workflow runs at all (it has never executed, and it triggers on `main` while the default branch is
   `master`; see open decision 8 in `docs/plan/project-plan.md`).
