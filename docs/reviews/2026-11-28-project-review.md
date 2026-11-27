# Project review, 2026-11-28

Independent review by an Opus subagent of Claude Code, covering `master` at `700f785` and the branch
`docs/test-protocol-v2`. The text below is the reviewer's report. It is advice, not a decision: it changes no
requirement, gate or plan.

- The finding IDs `F1` to `F17` are local to this document. They are not project IDs.
- The session that requested the review has **not** independently re-checked the findings.
- Line numbers refer to the commit above and will go stale.

## Method and limits

Read-only: no builds, no pytest, nothing written to the repository, nothing read under `data/`. The Python scorer and
parser were probed once with `python -B`, run from outside the repository. No Kotlin was run. Statements about Android
platform behaviour come from the reviewer's knowledge of the platform and are marked UNVERIFIED where it matters.

Tags: "confirmed" means checked in the files or by running code; "judgement" is the reviewer's assessment. Areas: Plan,
Impl-plan (architecture and approach), Assump (assumptions), Impl (code so far), Branch (applies only to
`docs/test-protocol-v2`).

## Summary

The repository is well organised and honest about what is unverified. But it has built and polished infrastructure
before taking any of the measurements that decide go/no-go. The tools that will produce the GATE-04 and GATE-09 numbers
also have defects that would make those numbers unreliable.

## Findings, ranked by impact on the go/no-go outcome

### F1 (critical; Plan, Assump): "measurement before building" has been reversed, so risk R7 has already happened

- **What is wrong:** The plan puts M-1 first: recordings, the demo APK test and desktop GATE-09, all before M0. None of
  that is done. Instead the time since 20 Nov went into M0, CI pinning, pre-commit, agent setup, a launcher icon
  (STEP-42) and an app label.
- **Evidence:** flowchart and next actions, `docs/plan/project-plan.md:38,42-45,262-265`; R7's early warning is
  "Recordings still not made", `project-plan.md:219`; `docs/plan/steps.md:27-29` shows STEP-P3, STEP-P4 and STEP-00
  still empty; STEP-18 (`steps.md:70`) is empty too; `git log` has no measurement work.
- **Status:** confirmed.
- **Recommendation:** freeze app work after STEP-05 until STEP-P3, STEP-00, STEP-18 and the benchmark in F2 are done.
  Record each in the results log.

### F2 (critical; Plan, Assump): the two biggest risks are not probed early, though both are cheap to test

- **What is wrong:**
  - R1 (LLM too slow, rated high/high) is first measured in STEP-23, inside M3, after about 20 sessions of other work.
  - R4 (background recording killed, rated critical) waits for the full STEP-06 to STEP-10 build.
  - GATE-07 (15 s for a ~1,500-token prompt) is the gate most likely to fail. Nobody has measured prompt-processing
    speed on the Tensor G2 (DEC-36 is OPEN).
- **Evidence:** `project-plan.md:213,216`; `steps.md:75`; `docs/decisions-log.md:46`.
- **Status:** judgement (ordering); the missing measurement is confirmed.
- **Recommendation:**
  - Now: push a prebuilt or NDK-built `llama-bench` to the phone over adb. Measure prompt processing and token
    generation for two candidate Q4 models, cold and after 10 minutes warm, while charging. Put the numbers into the
    latency formula in `docs/gates.md:19`. A one-day task with no app code.
  - Then: a throwaway 30-minute GATE-05 probe, a microphone foreground service that writes PCM to a file. That is
    roughly STEP-06 to STEP-08 minimal, before any session or VAD wiring.

### F3 (major; Impl): the GATE-04 scorer gives both false negatives and false positives

- **What is wrong:**
  - `normalize()` turns "p.m." into "p m", so correct transcripts miss the alternatives "3:30 pm", "6 pm" and
    "9:45 pm". Confirmed by running it: "Thursday at 3:30 p.m." does not match F06. That Whisper-style output often
    writes "p.m." is a judgement.
  - Each `requires` group is matched anywhere in a 30-minute transcript, with no proximity check. A made-up text with
    "deadline moved … the 30 minutes" scored F20 (a strict date) as found, and "memory … did not order pizza" scored
    F07.
  - "A-44-71" becomes "a 44 71" and misses F09.
- **Evidence:** `tools/src/voicetools/facts.py:13-31,58-62`; `docs/testing/planted-facts.json` (F06, F07, F09, F12,
  F14, F20).
- **Status:** confirmed by running it.
- **Recommendation:**
  - Normalise `a.m.`/`p.m.`.
  - Require the groups to co-occur within a window, such as the same SPEECH entry or ±N words.
  - Have the report print the strict numerator and denominator and the matched snippet, so a human can audit every hit.
  - Add tests for these cases, before the first real score.

### F4 (major; Plan): GATE-09 is weakly specified and statistically thin

- **What is wrong:**
  - Q8, the hedge question (the most likely false yes), is in the core set but neither GATE-09 criterion covers it. The
    criteria cover only Q9 to Q11 and "7 factual" questions, and Q1 to Q7 includes the negation question Q5. The test
    protocol says to flag Q8.
  - Exactly 5 of 7 correct is neither PASS (≥6) nor FAIL (<5), and the MARGINAL column is empty.
  - "Zero false yes" on only 3 never-said questions is weak evidence. By the rule of three, 0 of 3 is still consistent
    with a high false-yes rate.
  - The number of runs and the sampling settings are not specified.
- **Evidence:** `docs/gates.md:16`; `docs/testing/questions.json:60-91`; `docs/testing/test-protocol.md:62`.
- **Status:** confirmed (gaps); judgement (statistics).
- **Recommendation:** add Q8 to the false-yes criterion; define what 5/7 means; use 8 to 10 never-said or hedge
  questions; fix greedy decoding, or run 3 times and require zero false yes in every run. Confirm all of this under
  open decision 2 before measuring.

### F5 (major; Plan, Impl): "no gap" cannot be measured as specified

- **What is wrong:**
  - No document says how a gap or an AudioRecord overrun is detected (GATE-05, STEP-08, GATE-03 "audio dropped"), and
    the pipeline has no mechanism for it.
  - A noticed gap or kill is neither GATE-05 PASS ("no gap, no kill") nor FAIL ("any silent stop").
  - GATE-08's middle band ("throttling" that does not fail GATE-03/07, but is not "below severe") is undefined.
- **Evidence:** `gates.md:12,15`; `steps.md:48` ("no gaps in a 5-minute test");
  `android/core/.../CapturePipeline.kt:89-103`.
- **Status:** confirmed.
- **Recommendation:**
  - Compare samples delivered with elapsed monotonic time, and with the AudioRecord frame position (for example
    `AudioRecord.getTimestamp`; UNVERIFIED on this build, check the docs). Log every discontinuity as a GAP entry, and
    log silenced-client periods (F6).
  - Turn the result into a raw-WAV continuity check in `voicetools`.
  - Close the threshold holes.

### F6 (major; Impl-plan, Assump): pausing capture on audio-focus loss conflicts with the driving use case

- **What is wrong:**
  - DEC-06, STEP-31 and the state diagram pause capture and write a GAP when audio focus is lost.
  - Recording does not need audio focus. A navigation app takes transient focus for every spoken direction, so this
    design creates exactly the gaps GATE-05 forbids.
  - The real risk is the opposite one: a call or the assistant can make the system silence our capture.
- **Evidence:** `project-plan.md:161-162`; `steps.md:88`; `decisions-log.md:16`.
- **Status:** judgement. Android behaviour UNVERIFIED: the reviewer's understanding is that concurrent capture rules
  apply since Android 10, and that `AudioRecordingConfiguration.isClientSilenced()` via
  `AudioManager.registerAudioRecordingCallback` reports silencing. Confirm in the platform docs.
- **Recommendation:** use audio focus only for TTS playback; mark GAPs from silencing callbacks; add "Maps speaks while
  recording" and "incoming call" cases to GATE-05.

### F7 (major; Impl): data-loss risks in `CapturePipeline` contradict "raw audio first, never dropped"

- **What is wrong** (line numbers in `CapturePipeline.kt`):
  - (a) `append(SessionStart)` runs before `source.start()`. If the session store fails, no audio is ever recorded
    (`:75-76`).
  - (b) An exception from `vad.accept`/`flush` ends the whole run, so raw recording stops. This matters once Silero
    sits behind JNI (`:97,102`). The KDoc lists only source, raw and store failures as fatal.
  - (c) Disk writes (`raw.write`) and VAD inference run synchronously on the capture thread, with no buffer between the
    mic read and the disk. A storage stall, or CPU contention from the LLM, can overrun AudioRecord, and that loss goes
    undetected (F5) (`:92-97`).
  - (d) A store failure while appending speech is reported as "recognition failed" and capture carries on. That
    contradicts the KDoc (`:126` against `:31-33`).
  - (e) The recognition executor queue is unbounded and holds `ShortArray` segments. At a real-time factor above 1 the
    heap grows by about 115 MB per hour of backlog (calculation: 16 kHz × 2 bytes). STEP-15's "bounded queue" cannot
    also keep "never drop" unless segments can be re-read from the raw WAV files.
- **Status:** (a), (b), (d) confirmed by reading; (c), (e) judgement.
- **Recommendation:**
  - Start raw capture independently of the session store.
  - Guard the VAD, so that on failure it degrades to raw-only plus a problem marker.
  - Use a dedicated read thread with a ring buffer, plus a separate writer thread.
  - Have segments carry sample offsets into the raw files.
  - Fix the KDoc, and add tests for each case.

### F8 (major; Plan, Branch): the car environment, the real use case, is never gated

- **What is wrong:** on `master` the car run is "measured and reported, not gating yet", yet R3 (road noise) is rated
  medium/high and SC-1 is a 2-hour drive. On the branch, v2 makes the car recording optional entirely. A PASS in a
  quiet room says little about the product.
- **Evidence:** `test-protocol.md:69`; `project-plan.md:12,215`; branch `test-protocol.md` §2.3 and `gates.md`
  GATE-02.
- **Status:** confirmed.
- **Recommendation:** keep a short (10-minute) car clip as at least a MARGINAL-level gate input, or state in the
  go/no-go note that the decision covers quiet rooms only.

### F9 (major; Plan, Assump): thermal and memory are tested for 30 minutes, but the product claim is 2 hours

- **What is wrong:** GATE-08 runs 30 minutes with the LLM "loaded", not generating, while REQ-NFR-02 and SC-1 claim 2
  hours. Charging, a dashboard mount, navigation, ASR and the LLM together are the worst thermal case, exercised only
  in STEP-38 at the very end.
- **Evidence:** `gates.md:15`; `docs/requirements.md:71-72`; `steps.md:105`.
- **Status:** judgement.
- **Recommendation:** use the `WavAudioSource` real-time replay for a 2-hour desk soak, charging, with a question every
  5 minutes; log `dumpsys thermalservice` and PSS. Possible as soon as STEP-14 and STEP-22 exist.

### F10 (major; Plan, Impl-plan): the context and compression model does not fit the retrieval design

- **What is wrong:**
  - Questions use keyword retrieval (DEC-04, DEC-38) with roughly 1,500-token prompts.
  - REQ-CTX-01/04 describe a growing LLM context that fills up, then compression that "resets" it.
  - It is unclear what the fill bar would measure. Summarising about 2 hours of transcript with a 1-2B model on the
    phone may take far longer than any useful window (UNVERIFIED, no numbers exist).
  - M5 has no gate.
- **Evidence:** `requirements.md:52-56`; `decisions-log.md:14,48`; `steps.md:95-97`.
- **Status:** judgement.
- **Recommendation:** decide which context model applies (a design decision); estimate compression time from the F2
  prompt-processing numbers; if the estimate is impractical, drop compression from the POC (the plan already moves
  condensing to the desktop).

### F11 (major; Branch): v2 states the GATE-04 scoring wrongly, and the default fact file gives silent wrong scores

- **What is wrong:**
  - v2 says "with 10 facts, each fact is worth 10 percentage points". `strict_percent` counts only `strict_kinds`, and
    the v2 checklist includes self-correction, negation and hedge facts, which are not strict.
  - With about 6 strict facts, each is about 17 points, so the ≥90% PASS means zero misses.
  - `check-facts` defaults to the invented v1 example set, so forgetting `--facts` scores a real recording against fake
    facts with no warning.
- **Evidence:** branch `test-protocol.md:91`; `facts.py:80-86`; `tools/src/voicetools/cli.py:129,144`.
- **Status:** confirmed.
- **Recommendation:** fix the text; require at least about 10 strict facts, or report a count rather than a percentage;
  make `--facts` mandatory, or refuse the example set without an explicit flag.

### F12 (minor; Impl): Kotlin and Python parsers have drifted, and the tests cannot catch it

- **What is wrong:**
  - Python's `\s`/`\d` are Unicode-aware while Java's are ASCII by default. On the Python side (run), a header with a
    non-breaking space is rejected and Arabic-Indic digits parse as a time. Kotlin would do the opposite (reasoned from
    the regex semantics, not run).
  - Kotlin `parseTime` uses `toLong()` on `\d{2,}`, so a 20-digit hour throws `NumberFormatException`. That crashes
    `parse()` and `recover()` instead of reporting a problem (`SessionFormat.kt:24-26`).
  - Kotlin drops extra attributes and bodies on GAP/INTERRUPTED and fills in "unknown" or `VERSION`. Python keeps what
    is in the file (`SessionFormat.kt:158-175` against `tools/src/voicetools/session.py:157-168`).
  - Neither parser checks `format=`.
  - The parity tests are hand-mirrored assertions on a few fields, with no shared expected output.
- **Status:** confirmed (Python run; Kotlin read).
- **Recommendation:** add `fixtures/sessions/*.expected.json` (the full parse result) that both suites compare against;
  use ASCII-only classes in Python (`[0-9]`, `[ \t]`, or `re.ASCII`); bound the digit count in `TIME`; reject unknown
  `format=` values.

### F13 (minor; Impl): `.gitignore` will silently drop the native libraries at STEP-11

- **What is wrong:** the global `*.so` (line 48) and `lib/` (line 58) rules come from the Python template. Prebuilt
  sherpa-onnx or llama `.so` files in `jniLibs/` would not be committed, so fresh clones and STEP-41 CI break.
- **Status:** confirmed.
- **Recommendation:** scope those rules to `tools/`, or decide now on fetching AARs or prebuilts by script.

### F14 (minor; Impl, Assump): TTS echo pollutes the transcript that feeds retrieval

- **What is wrong:** with speaker output (the car without a headset), spoken answers are re-transcribed as SPEECH.
  Later questions can then retrieve the model's own claims as "what I said", a false-yes feedback loop. REQ-TTS-04 is
  only "S".
- **Status:** judgement.
- **Recommendation:** tag or suppress VAD segments that overlap the Speaking state.

### F15 (minor; Plan): traceability gaps

- **What is wrong:**
  - Step and gate detail lives in an external "detailed doc" the owner holds (`project-plan.md:4`; `steps.md:6`).
  - `TASK-00-7` is undefined (`project-plan.md:199`).
  - SC-1 has no gate and the decision matrix gives no outcome for it.
  - STEP-36 adds an export "metadata header" the format does not define; that header would be a parse problem.
  - Export sends Markdown only, while desktop re-transcription needs the raw audio.
  - DEC-01 names whisper.cpp, but no step integrates a second native ASR stack.
- **Status:** confirmed.
- **Recommendation:** bring the detailed plan into the repository, or drop the reference to it, and resolve each item.

### F16 (minor; Plan, Impl): stale or overstated docs (mostly understating the current state)

- **What is wrong:**
  - `AGENTS.md:11` says nothing has been measured on the phone, but GATE-01 was recorded.
  - `project-plan.md:3,258` still says "plan only" and "Android version unknown".
  - Open decision 8 says actions are "still pinned to tags", but `ci.yml` pins SHAs.
  - The `ci.yml:34` comment "Skipped until the Gradle wrapper…" is stale.
  - The KDoc in `CaptureService.kt`/`MainActivity.kt` and the header of `libs.versions.toml` say "never built", yet the
    app was built and installed on the phone.
  - The "11 tests" and "78 tests" counts in `skeleton-status.md`, `pinned-versions.md` and `CHANGELOG.md` are
    outdated.
  - CI uses unpinned `pip install` although `uv.lock` exists.
  - Whether CI has run on GitHub, and whether the action SHAs are right, is UNVERIFIED (not checked; STEP-40 is not
    done).
  - No record exists of the checkpoint 1 review that STEP-05 is waiting for.
- **Recommendation:** one documentation sync pass, run through `doc-auditor`.

### F17 (minor; Branch): other v2 inconsistencies

- **What is wrong:**
  - The agent is told to write `data/recordings/*.facts.json`, but `.claude/settings.json` denies it reading `data/`,
    so it cannot verify what it wrote.
  - That deny rule covers only the Read tool; `cat data/...` through Bash is not blocked.
  - `steps.md:60` (STEP-13) still says "desk recording".
  - v2 dropped the explicit "10 to 20 s mid-thought pauses" guidance that exercises REQ-CAP-04 and VAD tuning.
  - The branch carries duplicate copies of the pre-commit commits (`e83a58b`, `646f4ef`). The content matches `master`
    and the diff against the merge base touches only 5 files, so the merge should be clean, but history will show
    duplicates.
- **Status:** confirmed.
- **Recommendation:** fix these before merging; have the human paste the facts JSON instead.

## In good shape

- The source-of-truth order, the ID discipline, a fallback for every gate, and consistent UNVERIFIED labelling. The
  results log records raw commands and outputs rather than summaries.
- The session format spec has precise recovery rules: byte-offset cut, side file for dropped text, idempotent
  INTERRUPTED. Both languages test recovery from every prefix of a valid file.
- `CapturePipeline` tests cover raw-before-VAD ordering, a slow recognizer on a real thread, a non-interruptible drain,
  and propagation of `Error`. `WavAudioSource` has testable real-time pacing with no drift.
- Privacy and safety defaults: no INTERNET permission, `allowBackup=false`, data, models and audio blocked in
  pre-commit, actions and hooks pinned to SHAs, toolchain pins with sources.
- v2's idea of writing notes from memory before any transcript exists is a good guard against selection bias, and it
  removes the recording friction that is blocking F1.

## Top three actions

1. **Measure the killers before any more app code (F1, F2):** make the recordings (v2 room recording plus a 10-minute
   car clip); run STEP-00 and STEP-18 on the desktop; run `llama-bench` for two models on the Pixel 7 and compute the
   GATE-07 estimate; run a throwaway 30-minute background-mic probe with Maps navigating.
2. **Fix the measuring instruments, then confirm thresholds (open decision 2) before the first scored run (F3, F4, F5,
   F11):** scorer normalisation and proximity; the GATE-09 Q8 criterion, the 5/7 hole, more never-said questions and
   fixed decoding; gap detection for GATE-05 and the GATE-08 duration; decide whether the car recording gates.
3. **Redesign the capture path before STEP-07 and STEP-08 (F6, F7):** a read thread with a ring buffer and a separate
   writer thread; raw capture that does not depend on the session store or the VAD; overrun and silencing detection that
   writes GAP entries; drop "pause on audio-focus loss"; resolve the context and compression model (F10) before M5 is
   planned in detail.
