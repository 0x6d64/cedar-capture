# Go/no-go gates

All thresholds are **proposals** until a human confirms them. Confirm or change them before the first measurement, so
they are not adjusted after seeing results. Agents must not change thresholds and must not mark a gate PASS.

| Gate | Check | PASS | MARGINAL | FAIL |
| --- | --- | --- | --- | --- |
| GATE-01 | Record Android version and patch level of the Pixel 7 | n/a (fact) | | |
| GATE-02 | Room recording (10 to 15 min), notes, reference transcript and confirmed fact and question files exist (`testing/test-protocol.md` v2; car recording optional) | all exist | | missing |
| GATE-03 | Speech speed as real-time factor (processing time / audio time), sustained 30 min (the room recording replayed back to back) | < 0.5 | 0.5 to 1.0 | > 1.0, or audio dropped |
| GATE-04 | Confirmed numbers, names, codes, dates, times correct in the room-recording transcript | >= 90% | 75% to 90% | < 75% |
| GATE-05 | 30 min recording, screen locked, navigation app in foreground | no gap, no process kill | | any silent stop |
| GATE-06 | LLM prompt-processing and generation speed, per model, measured separately | numbers recorded | | |
| GATE-07 | Time from end of question to first spoken word, ~1,500-token prompt | <= 15 s | 15 to 30 s | > 30 s |
| GATE-08 | 30 min with speech and LLM loaded, phone charging: memory and thermal log | no kill, thermal status below severe | | kill, or throttling that fails GATE-03 or GATE-07 |
| GATE-09 | Answer quality on the 11 core questions | zero false yes on Q9-Q11, >= 6 of 7 factual correct | | any false yes, or < 5 of 7 factual correct |
| GATE-10 | Audio routes (optional, does not gate): built-in mic + Bluetooth out, Bluetooth hands-free, phone speaker | table of results | | |

Latency estimate before measuring end to end: `prompt_tokens / pp + first_sentence_tokens / tg` (pp = prompt-processing
tokens per second, tg = generation tokens per second; first sentence about 20 tokens), plus TTS start time.

## Decision matrix

1. GATE-03 or GATE-04 FAIL: change engine or model and rerun. If every candidate fails, the phone records audio only and
   the desktop transcribes.
2. GATE-05 FAIL: stop. The product depends on background recording.
3. GATE-07 or GATE-09 FAIL: consider retrieval-only answers (the app reads the best-matching passage aloud, no
   generation) or answers on the desktop after the drive.
4. GATE-07 MARGINAL: smaller model, fewer retrieved chunks, or an audio cue while waiting.
5. All PASS: continue to the next milestone.

Gates are decided by a human from measured numbers in `results/RESULTS-LOG.md`.
