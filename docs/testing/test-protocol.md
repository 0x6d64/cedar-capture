# Test protocol: ground-truth rambling recording

Purpose: one recording with known content, used for speech accuracy, "did I mention X" retrieval, false-yes detection
and compression validation (GATE-02, GATE-04, GATE-09). All details below are invented; keep the fact types.
Machine-readable versions: `planted-facts.json`, `questions.json`.

## 1. Rules for the recording

1. Do not read from a script. Reading sounds unlike rambling and gives falsely good results. Keep the fact list out of
   sight; glance at it only between thoughts and talk freely around the facts about anything else.
2. Include natural messiness: pauses of 10 to 20 seconds mid-thought, false starts, abandoned sentences, topic jumps.
3. Spread the facts over the whole session. F13 to F15 fall in the middle third (roughly minutes 12 to 18), F01 in the
   first minute, F20 in the last minute.
4. Say each fact once, in your own words. Do not repeat facts to make them easier.
5. Right after recording, write down roughly the minute at which you said each fact (logging sheet, section 4).
6. Record twice: (a) quiet desk, phone built-in microphone; (b) in the car with road noise, phone mounted, built-in
   microphone. Optional (c): Bluetooth headset in hands-free mode (for GATE-10).
7. Length: 30 minutes for runs (a) and (b). Optional repeat in German later (stretch goal) with translated facts.
8. Store as 16 kHz mono 16-bit WAV in `data/recordings/` (git-ignored).

## 2. Hidden fact list

| ID | Type | Fact | Position |
| --- | --- | --- | --- |
| F01 | number | The cabin rental costs 840 euros for the week | first minute |
| F02 | name | A colleague named Ilinca suggested the route | |
| F03 | date | The inspection is on March 14th | |
| F04 | hedge | "Maybe we should switch suppliers." Say clearly it is undecided | |
| F05 | decision | We decided to keep the old router | |
| F06 | self-correction | "The meeting is Tuesday, no, Thursday at 15:30" (correct: Thursday 15:30) | |
| F07 | negation | We did not order the extra memory | |
| F08 | magnitude | The drive is 12 kilometers, not 120 | |
| F09 | identifier | Ticket number A-4471 | |
| F10 | contrast | The blue folder holds contracts, the green folder holds invoices | |
| F11 | name + day | Dragos will call back on Monday | |
| F12 | time | The shop in Sibiu closes at 18:00 | |
| F13 | code | The warehouse code is K-7 | middle third |
| F14 | time | Mara's flight lands at 21:45 | middle third |
| F15 | number | The battery pack is 4,800 milliamp hours | middle third |
| F16 | term | Say "quantization" naturally in a sentence | |
| F17 | term | Say "foreground service" naturally in a sentence | |
| F18 | term | Say "voice activity detection" naturally in a sentence | |
| F19 | hedge | "I might cancel the trip." Undecided | |
| F20 | date | The deadline is the 30th | last minute |

## 3. Question set

Core set (questions 1 to 11) is gating (GATE-09). Questions 9 to 11 are never-said: the right answer is "not mentioned".
Questions 8 and 16 test whether a hedge turned into a decision. Questions 12 to 16 are the position set: reported, not
gating. See `questions.json` for expected answers.

## 4. Logging sheet (fill in right after recording)

One row per fact: ID, approximate minute said, exact words you used, recording run (a, b or c). Keep it as plain text next
to the audio (`data/recordings/<name>.facts.txt`).

## 5. Scoring

1. **Transcript check:** run `voicetools check-facts <transcript>`. Count numbers, names, codes, dates and times
   separately from the rest; digits and names are where small models fail. Optionally transcribe a 5-minute section by
   hand and compute word error rate.
2. **Question check:** mark each answer correct, wrong or missing. Flag every false yes on questions 8 to 11 and 16
   (never-said and hedge questions). A false yes is worse than a wrong or missing answer for this use case.
3. **Position check:** compare retrieval success for an early fact (Q1), middle-third facts (Q12 to Q14) and the
   last-minute fact (Q15).
4. **Compression check:** which facts survive, which are lost, which are distorted (for example a hedge turned into a
   decision). Method: mechanical checks of numbers, names and dates first; then claim-by-claim alignment with a model
   different from the one that wrote the summary; then the planted-facts checklist.
5. **Pass criteria:** see `docs/gates.md` (GATE-04 and GATE-09). The car run is measured and reported, not gating yet.

## 6. Optional benchmark audio

Read-speech corpora with known transcripts (public-domain audiobooks with their text, or Common Voice, which includes
German; UNVERIFIED, not checked) are useful for repeatable engine comparisons. They test raw recognition, not rambling.
A YouTube video is a weak choice: its captions are not reliable ground truth, and downloading may conflict with the
site's terms.
