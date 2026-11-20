# ADR-0004: Plain Markdown session format with tagged entries

- Status: Proposed
- Date: 2026-11-18
- Related: REQ-SES-01 to REQ-SES-04, REQ-CTX-03, DEC-28

## Context

Sessions are exported raw and condensed on a desktop. They must survive a killed app, be greppable, and keep the user's
speech, LLM questions and answers, and compression blocks clearly apart.

## Decision

One UTF-8 text file per session. Each entry is a header line `[TAG t=HH:MM:SS key=value]` followed by a body. Reversible
escaping of body lines. Full specification in `docs/session-format.md`. No database.

## Consequences

Simple append-only writes and easy desktop tooling. Parsing rules for truncated files must be implemented identically in
Kotlin and Python and are tested against shared fixtures (ADR-0005). Any change bumps `format=`.
