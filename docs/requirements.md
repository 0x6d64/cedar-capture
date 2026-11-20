# Requirements

Priority: M = must for the POC, S = should, C = could. "Check" names the gate or step that verifies it.

## Capture (CAP)

| ID | Pri | Requirement | Check |
| --- | --- | --- | --- |
| REQ-CAP-01 | M | The app transcribes microphone speech to text fully offline and continuously, with no network access required. | GATE-03, GATE-04 |
| REQ-CAP-02 | M | Each transcript segment carries a timestamp relative to the session start. | STEP-03 |
| REQ-CAP-03 | M | Recording continues with the screen locked and while another app (for example a navigation app) is in the foreground. The user starts recording while the app is visible, because Android does not allow a background start of a microphone foreground service. | GATE-05 |
| REQ-CAP-04 | M | Long pauses mid-thought do not end the session. Only an explicit stop ends it. | STEP-13 |
| REQ-CAP-05 | S | Recording survives Bluetooth headset connect or disconnect without losing already captured text. | GATE-10 |
| REQ-CAP-06 | S | Stop and start can be triggered from a headset button. | STEP-32 |
| REQ-CAP-07 | M | Raw audio is kept on the phone for the session, so transcription can be redone later. | STEP-09 |
| REQ-CAP-08 | S | The input microphone can be chosen explicitly and defaults to the built-in microphone. | STEP-30 |

## Session (SES)

| ID | Pri | Requirement | Check |
| --- | --- | --- | --- |
| REQ-SES-01 | M | A session is one plain-text Markdown file stored locally on the phone (see `session-format.md`). | STEP-03 |
| REQ-SES-02 | M | The session file is written incrementally; a crash loses at most the last few seconds. | STEP-10 |
| REQ-SES-03 | M | Every entry is tagged by origin: user speech, LLM question, LLM answer, compression block. Origins are distinguishable by plain-text search. | STEP-03 |
| REQ-SES-04 | M | The raw transcript is never modified or deleted by compression. | STEP-34 |

## LLM questions (LLM)

| ID | Pri | Requirement | Check |
| --- | --- | --- | --- |
| REQ-LLM-01 | M | The user can trigger a question by voice or button. The question is transcribed and logged as a question entry. | STEP-24 |
| REQ-LLM-02 | M | A local LLM runs on the phone without network access and answers using the session content as context. | GATE-06, GATE-07 |
| REQ-LLM-03 | M | The answer is logged into the session as an answer entry. | STEP-24 |
| REQ-LLM-04 | M | Answers are short by default. | STEP-20 |
| REQ-LLM-05 | S | Recording continues while the LLM is generating, or any gap is logged explicitly. | STEP-29 |
| REQ-LLM-06 | C | Streaming generation. Deferred; decide after measuring speed. | GATE-07 |
| REQ-LLM-07 | M | When the retrieved passages do not contain the answer, the LLM says the topic was not mentioned and does not invent an answer. | GATE-09 |

## Speech output (TTS)

| ID | Pri | Requirement | Check |
| --- | --- | --- | --- |
| REQ-TTS-01 | M | Each LLM answer is read aloud through the active audio output (phone speaker, wired or Bluetooth headset). | STEP-27 |
| REQ-TTS-02 | M | TTS never speaks the user's own transcript. | STEP-27 |
| REQ-TTS-03 | S | The user can stop speech output immediately. | STEP-27 |
| REQ-TTS-04 | S | Playback is not re-transcribed as user speech (echo handling). Unverified how hard this is on the Pixel 7. | GATE-10 |

## Context and compression (CTX)

| ID | Pri | Requirement | Check |
| --- | --- | --- | --- |
| REQ-CTX-01 | M | The UI shows how full the LLM context is, as a percentage, based on the loaded model's own tokenizer. | STEP-33 |
| REQ-CTX-02 | S | A configurable warning threshold alerts the user as the context nears full. | STEP-33 |
| REQ-CTX-03 | M | The user can trigger compression. The app writes a start marker, the compression output, then an end marker into the session. | STEP-34 |
| REQ-CTX-04 | M | After compression, the LLM context starts from the compression output plus everything after the end marker. | STEP-34 |
| REQ-CTX-05 | M | Markers contain timestamp, model name and the transcript range that was compressed. | STEP-34 |

## Export (EXP)

| ID | Pri | Requirement | Check |
| --- | --- | --- | --- |
| REQ-EXP-01 | M | The user can export a session as a Markdown file via the Android share sheet. | STEP-36 |
| REQ-EXP-02 | S | A failed or deferred export never deletes the local session. | STEP-36 |
| REQ-EXP-03 | C | Automatic queued upload to Google Drive when a connection is available. Post-POC. | not in POC |

## Non-functional (NFR)

| ID | Pri | Requirement | Check |
| --- | --- | --- | --- |
| REQ-NFR-01 | M | Core features (capture, LLM, TTS, context) work in airplane mode. | STEP-38 |
| REQ-NFR-02 | M | A 2-hour recording completes without crash or loss, with the phone on a charger. | STEP-38 |
| REQ-NFR-03 | M | Memory use does not grow without bound during a 2-hour session. | GATE-08 |
| REQ-NFR-04 | S | Transcription keeps up with real time on the Pixel 7. Unverified. | GATE-03 |
| REQ-NFR-05 | S | The app logs per-stage timing: transcription lag, LLM time to first token, tokens per second, TTS time to first audio. | STEP-23 |
