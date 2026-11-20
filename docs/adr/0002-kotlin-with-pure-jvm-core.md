# ADR-0002: Kotlin app with a pure-JVM core module

- Status: Proposed
- Date: 2026-11-18
- Related: DEC-17, DEC-21, DEC-22, DEC-29

## Context

The speech and LLM engines are native libraries either way. The domain logic (session format, chunking, prompt budget,
compression markers, route policy) needs fast, repeatable tests without a phone or emulator.

## Decision

Kotlin for the app, Jetpack Compose for UI, a thin C++ layer through the NDK where needed. Domain logic lives in
`android/core`, a pure Kotlin/JVM module with no Android imports, no third-party runtime dependencies and no coroutines.
Blocking calls and callbacks are used in `core` interfaces; Flow wrappers live in `app`.

## Consequences

`./gradlew :core:test` runs in seconds on any machine with a JDK. Some interfaces are less elegant than Flow-based
ones. Revisit if wrapping becomes a burden.
