# Native code (empty on purpose)

STEP-11 adds the speech engine's native libraries; STEP-21 adds llama.cpp (start from its Android example). Add a
`CMakeLists.txt` and `externalNativeBuild` to `app/build.gradle.kts` only when the first native library is added, and
check 16 KB page alignment of every library (DEC-19). Third-party sources go to `third_party/` as submodules, never copied.
