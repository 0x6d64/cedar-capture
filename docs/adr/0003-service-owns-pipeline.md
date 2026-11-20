# ADR-0003: The capture service owns the pipeline; raw audio is written first

- Status: Proposed
- Date: 2026-11-18
- Related: REQ-CAP-03, REQ-CAP-07, DEC-03, DEC-22, DEC-23

## Context

The screen can be off and the activity destroyed while recording continues. Speech recognition and LLM generation compete
for the same CPU cores. Android may kill background work.

## Decision

A foreground service (microphone type) owns the audio source, the raw audio writer, the recognition queue, the session
store and the engines. The UI observes state. Raw audio is written to disk before anything else, so transcription can lag
and catch up. The LLM and TTS are never allowed to block capture. The LLM sits behind an interface so it can move to a
separate process if native crashes or memory kills appear.

## Consequences

More code in the service than in a ViewModel-centric design. A recognizer that falls behind costs latency, not data.
GATE-05 (background recording survives) can stop the project.
