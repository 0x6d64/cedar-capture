# Decision log (research)

Status: ANSWERED = evidence is enough to choose a POC default; PARTIAL = evidence helps but a Pixel 7 measurement is still required;
DESIGN = a design choice, no research can answer it; OPEN = not resolved. Evidence comes mostly from other devices, vendor posts,
community notes and a few official pages. Treat it as direction, not proof, and re-verify before relying on a claim.

Accepted architecture decisions live in `adr/`. This log records the research behind them.

| ID | Topic | Status | POC default |
| --- | --- | --- | --- |
| DEC-01 | STT model and chunking | PARTIAL | Benchmark harness first: sherpa-onnx with Moonshine vs whisper.cpp tiny/base on the Pixel 7 |
| DEC-02 | LLM runtime and model | PARTIAL | llama.cpp with a 1-2B Q4 model; test one LiteRT-LM Gemma model as second candidate |
| DEC-03 | Concurrency and RAM | PARTIAL | Always keep raw audio so transcription can lag and catch up |
| DEC-04 | Context strategy | DESIGN | Compression plus keyword search over the full transcript |
| DEC-05 | Question trigger | DESIGN | On-screen and headset button; no wake phrase yet |
| DEC-06 | Playback vs recording | DESIGN | Keep recording during TTS if the route allows; otherwise pause with a gap marker |
| DEC-07 | Compression location | DESIGN | On the phone with the same LLM; validate on the desktop later |
| DEC-08 | TTS engine | PARTIAL | System TTS first (see DEC-34) |
| DEC-09 | Audio handling | OPEN | 16 kHz mono; test with and without headset (see DEC-32, DEC-33) |
| DEC-10 | Raw audio storage | ANSWERED | Uncompressed WAV chunks; about 230 MB for 2 hours (calculation) |
| DEC-11 | Export path | DESIGN | Share sheet first; Drive later |
| DEC-12 | Driving UI | DESIGN | Audio cues; every driving action works without looking at the screen |
| DEC-13 | Model delivery | DESIGN | Push models over USB for the POC |
| DEC-14 | Instrumentation and pass criteria | DESIGN | Log RTF, time to first token, tokens/s, TTS start; accuracy measured separately |
| DEC-15 | Desktop prototype fidelity | PARTIAL | Use the same engine families on desktop and phone |
| DEC-16 | Language hooks | PARTIAL | UTF-8 sessions, language tag per segment, avoid English-only engine choices |
| DEC-17 | Kotlin vs Java vs C++ | DESIGN | Kotlin app, thin C++ glue via NDK |
| DEC-18 | Native integration | PARTIAL | Library's own Android code for speech; thin JNI based on the llama.cpp Android example for the LLM |
| DEC-19 | ABI and build setup | PARTIAL | arm64-v8a only; 16 KB page alignment for all native libraries (confirm in Google's docs) |
| DEC-20 | Min and target SDK | ANSWERED | targetSdk 36; minSdk after GATE-01 |
| DEC-21 | UI toolkit | DESIGN | Jetpack Compose |
| DEC-22 | Concurrency model | DESIGN | Coroutines in the app, dedicated capture thread; the service owns the engine |
| DEC-23 | Process isolation | DESIGN | Single process first; LLM behind an interface |
| DEC-24 | Model lifecycle | PARTIAL | Keep both loaded if RAM allows; log thermal status |
| DEC-25 | Capture API and audio source | PARTIAL | AudioRecord 16 kHz mono, VOICE_RECOGNITION, A/B against MIC |
| DEC-26 | Voice activity detection | ANSWERED | Silero via sherpa-onnx; long configurable end-of-speech silence, tune on real recordings |
| DEC-27 | Headset button handling | PARTIAL | MediaSession in the service; log key events before mapping |
| DEC-28 | Session storage | DESIGN | Append-only Markdown plus raw audio chunks; no database |
| DEC-29 | Shared core with desktop prototype | DESIGN | Do not share code; share the format spec and fixtures |
| DEC-30 | Replay test mode | DESIGN | AudioSource with microphone and WAV implementations |
| DEC-31 | Licensing | PARTIAL | Check model and library licenses before sharing |
| DEC-32 | Bluetooth route and microphone | PARTIAL | Built-in mic with Bluetooth output; hands-free SCO input may be 8 kHz (community notes, unconfirmed) |
| DEC-33 | Echo while TTS plays | PARTIAL | Do not rely on echo cancellation; headset or Bluetooth output; test speaker separately |
| DEC-34 | TTS engine (English) | PARTIAL | System TTS sentence by sentence; Piper via sherpa-onnx as fallback; skip Kokoro |
| DEC-35 | German transcription | PARTIAL | English first; try German fine-tuned Whisper tiny/small later on own voice |
| DEC-36 | Real LLM speed on Pixel 7 | OPEN | No benchmark found; measure with llama.cpp's benchmark tool plus end-to-end timing |
| DEC-37 | Token counting for fill indicator | DESIGN | Count with the loaded model's tokenizer; estimate until loaded |
| DEC-38 | Retrieval over the transcript | PARTIAL | In-memory keyword scoring over chunks, top 3-5; add embeddings only if paraphrases are missed |
| DEC-39 | Validating compression | ANSWERED | Mechanical checks, claim alignment with a different model, planted-facts checklist |
