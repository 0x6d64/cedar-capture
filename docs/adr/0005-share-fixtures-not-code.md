# ADR-0005: Share fixtures, not code, between Kotlin and Python

- Status: Proposed
- Date: 2026-11-18
- Related: DEC-15, DEC-29

## Context

The desktop tooling and the phone app both read session files. Kotlin Multiplatform or a shared C++ core would couple the
build systems and slow the desktop work.

## Decision

Two small independent implementations of the session reader/writer, kept honest by shared input files in `fixtures/`
and a written specification. Both test suites must pass on the same fixtures.

## Consequences

Some duplicated logic. A change to the format touches the spec, both implementations and the fixtures in one change.
