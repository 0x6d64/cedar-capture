# Work steps

Who: **A** = an agent can finish it alone (JVM or Python only), **S** = needs the Android SDK/NDK on the machine,
**D** = needs the physical Pixel 7, **H** = needs a human action or decision. Several letters mean several kinds of work.
Status column: update to `done` with the commit hash when the exit check passed. Review checkpoints are marked in bold.
Source of detail: the project plan and the detailed technical plan the owner holds; library details marked UNVERIFIED
must be checked first.

## Preparation

| ID | Step | Who | Exit check | Status |
| --- | --- | --- | --- | --- |
| STEP-P1 | Install current stable Android Studio with NDK and CMake; enable USB debugging; `adb devices` lists the phone | H | phone listed | done |
| STEP-P2 | Note Android version and patch level (GATE-01) | H | recorded in results log | done |
| STEP-P3 | Collect recordings (GATE-02), voice-detection model, candidate speech models; convert to 16 kHz mono WAV | H | files in `data/`, `models/` | |
| STEP-P4 | Download the newest sherpa-onnx Android release (use the newest tag; an older write-up's version number is UNVERIFIED) | H | archive present | |
| STEP-00 | Install a pre-built sherpa-onnx demo APK and try your recording through a laptop speaker | H D | first impression of speed and accuracy noted | |

## M0 Skeleton and replay

| ID | Step | Who | Exit check | Status |
| --- | --- | --- | --- | --- |
| STEP-01 | Verify and bump placeholder versions in `android/gradle/libs.versions.toml`; generate the Gradle wrapper; make `:app` build | S | app installs and starts on the Pixel 7 | done a55f782 |
| STEP-02 | Pure-Kotlin `core` module (exists in skeleton; verify it builds and tests run on JVM) | A | `./gradlew :core:test` green | |
| STEP-03 | Session format writer and parser with tests (skeleton has a first version; compare with `docs/session-format.md` and `fixtures/`) | A | round trip, truncated file, interrupted marker tests pass in Kotlin and Python | |
| STEP-04 | WAV replay `AudioSource`: 512-sample frames (32 ms) at 16 kHz mono PCM16, real-time or fast pacing; fake recognizer and VAD | A | unit test runs a WAV fixture to segments | |
| STEP-05 | Pipeline orchestrator with fakes: source, raw writer, VAD, recognition queue, session store; state machine | A | fixture WAV through the pipeline gives the expected session file. **Checkpoint 1** | |

## M1 Capture

| ID | Step | Who | Exit check | Status |
| --- | --- | --- | --- | --- |
| STEP-06 | Manifest, permissions (microphone, foreground service types, notification), notification channel, permission flow; RECORD_AUDIO granted before the service goes foreground | S | denied permission shows a message, no crash | |
| STEP-07 | `CaptureService` foreground (microphone type), notification Stop action, started only while app visible, observable state | S D | survives 5 min, then 30 min screen locked with a navigation app (GATE-05) | |
| STEP-08 | `MicAudioSource`: AudioRecord 16 kHz mono PCM16, VOICE_RECOGNITION with a MIC switch, dedicated read thread | S D | no gaps in a 5-minute test | |
| STEP-09 | Raw audio writer: chunked WAV (about 5 min per file, my proposal), header repair after a kill | A D | force-stop; files play on the desktop, at most the last seconds lost | |
| STEP-10 | Session store on disk: append and flush per entry; interrupted marker on recovery | A D | force-stop test keeps the file valid. **Checkpoint 2** | |

## M2 Transcription

| ID | Step | Who | Exit check | Status |
| --- | --- | --- | --- | --- |
| STEP-11 | Add sherpa-onnx native libraries and Kotlin API; proguard keep rules; 16 KB page alignment check of every native library | S | app loads native libs; alignment documented | |
| STEP-12 | Push models to app storage with adb; load by file path | H D | files readable by the app | |
| STEP-13 | Voice detection wrapper (Silero via the library): long configurable end-of-speech silence (start 2 s, a guess), min speech 0.15 s, max speech duration cap | S D | segment boundaries fall at natural pauses on the desk recording | |
| STEP-14 | Recognizer wrapper for the first model under test (baseline: Whisper tiny or base) | S D | replay of desk recording yields a transcript; real-time factor logged | |
| STEP-15 | Recognition queue with bounded threads; lag shown in UI | S | with a slowed recognizer, recording continues and text catches up | |
| STEP-16 | Replay action in the app plus the desktop scorer (`voicetools check-facts`) | A D | one command produces a score per run | |
| STEP-17 | Compare engines, tune silence setting, fill results log for GATE-03 and GATE-04; run the car recording | D H | written engine decision. **Checkpoint 3** | |

## M3 Questions and answers

| ID | Step | Who | Exit check | Status |
| --- | --- | --- | --- | --- |
| STEP-18 | Desktop answer-quality run (GATE-09): candidate 1 to 2 billion parameter models, keyword retrieval, the core 11 questions | A H | table of model vs answers, false yes marked | |
| STEP-19 | Chunker and keyword retriever in `core` (about 100 to 150 words, 20 overlap: a starting guess) | A | planted-fact questions return the right chunk in top 3 | |
| STEP-20 | Prompt builder and token budget in `core` behind a tokenizer interface; instruction "not mentioned if absent" | A | tests for fits, over-budget, empty retrieval | |
| STEP-21 | Native build of llama.cpp for arm64-v8a (start from its Android example; NDK current; 16 KB alignment) | S | library loads on the phone | |
| STEP-22 | `LlmEngine` implementation: two-stage load, mutex, cancellable generation, tokenizer count | S D | 20 load/generate/unload cycles with stable memory | |
| STEP-23 | In-app speed log: prompt tokens, time to first token, tokens per second; separately run llama.cpp's benchmark tool (GATE-06) | S D | log with both sets of numbers | |
| STEP-24 | Question flow: trigger, Thinking state, next utterance as question, retrieve, prompt, generate, log Q and A; typed-question field in debug builds | S | replay plus typed Q1 to Q11 produce tagged entries | |
| STEP-25 | Debug trace per question: retrieved chunks, final prompt, raw output | S | each question has a trace file | |
| STEP-26 | Phone gates GATE-07 and GATE-09, results log | D H | numbers recorded. **Checkpoint 4** | |

## M4 Speech output and routes

| ID | Step | Who | Exit check | Status |
| --- | --- | --- | --- | --- |
| STEP-27 | `TextToSpeaker` on Android TTS: wait for init, sentence by sentence with queue-add, stop discards queue, shutdown, offline voice check, TTS service in manifest queries | S D | answer spoken; stop within about a second | |
| STEP-28 | Sentence splitter in `core` (abbreviations, decimals, "4,800", input length limit) | A | unit tests | |
| STEP-29 | Route policy: continue capture or pause with gap marker per audio route | A | unit tests with fake routes; gap markers appear | |
| STEP-30 | Input device selection (built-in default), Bluetooth route handling, route tests (GATE-10) | S D H | table of routes with results | |
| STEP-31 | Audio focus: pause capture and write gap marker on loss, resume after | S D | test call leaves a valid session | |
| STEP-32 | MediaSession for headset button: log key events first, then map | S D H | headset and car trigger mapped actions in a 10-minute test. **Checkpoint 5** | |

## M5 Context and compression

| ID | Step | Who | Exit check | Status |
| --- | --- | --- | --- | --- |
| STEP-33 | `ContextManager`: used tokens, context size, warning threshold; fill bar | S | matches a manual count within a small margin | |
| STEP-34 | Compression: chunked summary, start/end markers, context reset; retrieval keeps searching the raw transcript | A S | marker parse tests; raw transcript byte-identical | |
| STEP-35 | Desktop validation script: mechanical checks, claim alignment with a different model, planted-facts checklist | A | report lists survived, lost, distorted facts. **Checkpoint 6** | |

## M6 Export and field test

| ID | Step | Who | Exit check | Status |
| --- | --- | --- | --- | --- |
| STEP-36 | Share-sheet export of the session Markdown with a short metadata header | S | opens on desktop with tags intact; failed share never deletes the session | |
| STEP-37 | Metrics sampling: memory, thermal status, per-stage timings into a log | S D | complete log for a 30-minute run | |
| STEP-38 | 30-minute soak then 2-hour drive (charging, mounted, navigation app, your headset) | D H | complete session file and metrics log for 2 hours | |
| STEP-39 | Fill results log for GATE-01 to GATE-10, write the one-page go/no-go note | H | note written | |
