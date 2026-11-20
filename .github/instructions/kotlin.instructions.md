---
applyTo: "android/**/*.kt"
---
Idiomatic Kotlin: immutable data classes, sealed interfaces, no `!!`. `android/core` must not import Android classes or
add runtime dependencies. One owner per native handle. The foreground service owns the pipeline; the UI only observes.
