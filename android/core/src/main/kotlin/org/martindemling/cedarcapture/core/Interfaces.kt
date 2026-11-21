package org.martindemling.cedarcapture.core

// Capability interfaces from the plan (docs/architecture.md). Sketches: blocking calls and callbacks so that this module
// stays dependency-free (ADR-0002). Android and native implementations live in :app. Change only with human approval.

/** A stretch of speech cut out by the voice activity detector. */
class AudioSegment(val startSeconds: Double, val samples: ShortArray)

data class TranscriptSegment(val startSeconds: Double, val endSeconds: Double, val text: String)

/** A passage of the raw transcript that the retriever can return. */
data class Chunk(val startSeconds: Long, val text: String)

/** 16 kHz mono PCM16 frames. Microphone and WAV replay are the two implementations (DEC-30). */
interface AudioSource {
    val sampleRate: Int
    fun start()

    /** Blocking read into [buffer]; returns the number of samples read, or -1 at the end of the source. */
    fun read(buffer: ShortArray): Int
    fun stop()
}

sealed interface VadEvent {
    data object Silence : VadEvent
    data object SpeechStarted : VadEvent
    class SpeechEnded(val segment: AudioSegment) : VadEvent
}

interface VoiceActivityDetector {
    fun accept(frame: ShortArray): VadEvent
}

interface SpeechRecognizer {
    fun transcribe(segment: AudioSegment): TranscriptSegment
}

/** Append-only store of session entries (REQ-SES-02: flush after every entry). */
interface SessionStore {
    fun append(entry: SessionEntry)
    fun entries(): List<SessionEntry>
    fun close()
}

interface LlmEngine {
    fun load(modelPath: String, contextSize: Int)

    /** Counts tokens with the loaded model's own tokenizer (DEC-37). */
    fun countTokens(text: String): Int

    /** Generates a reply, calling [onText] for each piece. Must be cancellable with [cancel]. */
    fun generate(prompt: String, onText: (String) -> Unit)
    fun cancel()
    fun unload()
}

interface Retriever {
    fun index(chunks: List<Chunk>)
    fun search(query: String, k: Int): List<Chunk>
}

interface TextToSpeaker {
    val isSpeaking: Boolean
    fun speak(sentence: String)
    fun stop()
}
