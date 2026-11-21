# Test protocol v2: free rambling recording, facts confirmed afterwards

Status: version 2, decided by the human on 2026-11-21. It replaces version 1 (a planted-fact script recorded twice for
30 minutes, in a room and in a car).

## Purpose

This is not a smoke test. It is the ground truth for these measurements:

| Check | Uses | Gate or step |
| --- | --- | --- |
| Speech accuracy on numbers, names, codes, dates and times | confirmed facts | GATE-04 |
| Answer quality, especially false yes on things never said | question set | GATE-09 |
| Sustained speed | the recording, replayed three times back to back (30 to 45 min of audio) | GATE-03 |
| Early, middle and late facts | fact positions | reported, not gating |
| Compression (later) | confirmed facts | STEP-34, STEP-35 |

Version 2 makes recording easy: you ramble freely and plant nothing. The cost moves to afterwards, when you confirm a
reference transcript and the facts. A replayed loop repeats its content, so for compression it is weaker evidence than one
long recording. This is a known gap.

## 1. Before recording (2 minutes)

Glance at this checklist of **fact types**. Then put it away. Do not plan exact sentences. Work at least one of each into
your rambling, the way you would naturally:

| Type | Example of the kind of thing |
| --- | --- |
| number | a price or an amount |
| magnitude | "12 kilometers, not 120" |
| code or identifier | "K-7", "A-4471" |
| person's name | German or English names, whichever you say naturally |
| date | "March 14th" |
| clock time | "15:30" |
| self-correction | "Tuesday, no, Thursday" |
| negation | something you did **not** do |
| hedge | a "maybe" that is not a decision |

Say something from the checklist in the first minute, and something in the last minute.

## 2. Recording

1. **Language:** English, with names in German or English.
2. **Length:** 10 to 15 minutes.
3. **Place and microphone:** a room, with the phone's built-in microphone. The car recording is optional and comes later.
   It is reported but does not gate.
4. **How to talk:** do not read from a script. Pauses, false starts and topic jumps are welcome; they are what the app
   must handle.
5. **Privacy:** real rambling can contain personal details. The recording and everything derived from it stays in `data/`,
   which is git-ignored. Talk about things you would not mind a few people reading.
6. **Format:** convert to 16 kHz mono 16-bit WAV. One way, not yet run in this project:
   `ffmpeg -i input.m4a -ar 16000 -ac 1 -c:a pcm_s16le data/recordings/<name>.wav`.

## 3. Right after recording, before any transcript (5 minutes)

Write down from memory the facts you remember saying, each with a rough minute, in `data/recordings/<name>.notes.txt`.

Do this before anyone or anything transcribes the audio. Facts are later chosen from this list, so they cannot be chosen
because a speech engine happened to get them right. That would inflate GATE-04.

## 4. Reference transcript (the main effort)

Make a transcript you trust, at `data/recordings/<name>.reference.txt`. Either type it yourself, or correct a draft
**while listening** to the audio.

The draft must not come from the engine under test. A desktop transcription tool would be a new dependency and needs an
ADR. The agent cannot listen to audio and cannot read `data/` (blocked in `.claude/settings.json`). You share with it only
what you choose to paste.

## 5. Choosing facts and questions (with the agent)

1. Paste your notes from section 3, plus the passages of the reference transcript around each fact, into the chat.
2. The agent proposes about 10 facts:
   - at least one of each type in section 1
   - spread across early, middle and late positions
   - each with accepted spellings, for example `["jorg", "joerg"]` (the scorer strips accents, so "Jörg" matches `jorg`)
3. The agent proposes 11 core questions in the GATE-09 shape:
   - questions 1 to 7: factual, each about a confirmed fact
   - question 8: a hedge question, "did I decide …?" for the "maybe" fact
   - questions 9 to 11: never-said questions, each about something plausible that you did **not** mention. Check them
     against the full reference transcript.
   - optional questions from 12 on (for example the negation and the last-minute fact): reported, not gating
4. You confirm or edit every fact and question. Only confirmed entries count.
5. The agent writes both files to `data/recordings/<name>.facts.json` and `data/recordings/<name>.questions.json`. They
   use the same format as `planted-facts.json` and `questions.json` in this folder.

## 6. Scoring

1. **Transcript check (GATE-04):** `voicetools check-facts <transcript> --facts data/recordings/<name>.facts.json`.
   Numbers, names, codes, dates and times count separately from the rest (`strict_kinds`). With 10 facts, each fact is
   worth 10 percentage points.
2. **Question check (GATE-09):** mark each answer correct, wrong or missing. Flag every false yes on the hedge and
   never-said questions. A false yes is worse than a wrong or missing answer for this use case.
3. **Position check:** compare an early, a middle and the last-minute fact. Reported, not gating.
4. **Sustained speed (GATE-03):** replay the WAV three times back to back and measure the real-time factor over the whole
   run.
5. **Pass criteria:** see `docs/gates.md`.

## 7. The example set in this folder

`planted-facts.json` and `questions.json` hold an invented example set from protocol version 1 (20 facts, 16
questions). The Python tests use them to check the scorer and the file format. They are not the ground truth for a real
recording. Do not edit them to make a test pass.

## 8. Optional benchmark audio

Read-speech corpora with known transcripts, such as public-domain audiobooks with their text or Common Voice, are useful
for repeatable engine comparisons. This is UNVERIFIED; not checked. They test raw recognition, not rambling. A YouTube
video is a weak choice: its captions are not reliable ground truth, and downloading may conflict with the site's terms.
