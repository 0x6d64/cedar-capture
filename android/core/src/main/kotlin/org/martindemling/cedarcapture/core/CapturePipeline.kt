package org.martindemling.cedarcapture.core

import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executor
import java.util.concurrent.RejectedExecutionException
import kotlin.math.roundToLong

/**
 * Where the capture pipeline is. Later steps add `Paused` (STEP-29, STEP-31), `Thinking` (STEP-24) and `Speaking`
 * (STEP-27) together with the behaviour that needs them. Allowed moves: Idle to Recording, Recording to Stopped, and
 * Idle or Recording to Error. Stopped and Error are final.
 */
sealed interface PipelineState {
    data object Idle : PipelineState
    data object Recording : PipelineState
    data object Stopped : PipelineState
    data class Error(val message: String) : PipelineState
}

/**
 * The capture path (ADR-0003, STEP-05): source, then raw audio, then voice detection, then a recognition queue, then
 * the session store. Pure JVM, no coroutines.
 *
 * - **Raw audio first.** Every frame goes to [raw] before the detector sees it. Recognition runs on
 *   [recognitionExecutor], so a slow recognizer costs latency, never audio. The capture loop never waits for it.
 * - **Order.** Session entries are chronological only when [recognitionExecutor] runs tasks in submission order, as a
 *   single-thread executor does. STEP-15 replaces it with a bounded queue.
 * - **Failures.** A recognizer failure is reported through [onProblem] and capture continues; that segment is missing
 *   from the session but present in the raw audio. A failure of the source, [raw] or [store] ends the run in
 *   [PipelineState.Error]. [run] never throws for those.
 * - **Ownership.** [run] stops [source] and closes [raw] and [store] when it ends, whatever the outcome.
 *
 * [run] blocks on the calling (capture) thread and may be called once. [stop] and [state] are safe from any thread.
 * [onState] and [onProblem] are called on the capture thread, or on a recognition thread for problems.
 * Session entry times have a resolution of one second (the segment start, rounded).
 */
class CapturePipeline(
    private val source: AudioSource,
    private val raw: RawAudioSink,
    private val vad: VoiceActivityDetector,
    private val recognizer: SpeechRecognizer,
    private val store: SessionStore,
    private val recognitionExecutor: Executor,
    private val sessionId: String,
    private val startedIso: String,
    private val frameSize: Int = DEFAULT_FRAME_SAMPLES,
    private val onState: (PipelineState) -> Unit = {},
    private val onProblem: (String) -> Unit = {},
) {
    @Volatile
    var state: PipelineState = PipelineState.Idle
        private set

    @Volatile
    private var stopRequested = false
    private val storeLock = Any()
    private val pending = mutableListOf<CountDownLatch>() // touched only by the thread that calls run()

    /** Asks [run] to finish: it ends after the frame being processed, flushes the detector and drains recognition. */
    fun stop() {
        stopRequested = true
    }

    fun run() {
        check(state == PipelineState.Idle) { "a pipeline runs only once" }
        var failure: Throwable? = null
        try {
            append(SessionStart(sessionId, startedIso))
            source.start()
            transition(PipelineState.Recording)
            capture()
        } catch (e: Exception) {
            failure = e
        } finally {
            finish(failure)
        }
    }

    private fun capture() {
        val frame = ShortArray(frameSize)
        while (!stopRequested) {
            val count = source.read(frame)
            if (count < 0) break
            if (count == 0) continue
            raw.write(frame, count) // raw audio first
            // The detector gets its own copy, so it may keep the frame.
            when (val event = vad.accept(frame.copyOf(count))) {
                is VadEvent.SpeechEnded -> submit(event.segment)
                VadEvent.SpeechStarted, VadEvent.Silence -> Unit
            }
        }
        vad.flush()?.let { submit(it) } // the last words must not be lost
    }

    private fun submit(segment: AudioSegment) {
        val done = CountDownLatch(1)
        pending.add(done)
        try {
            recognitionExecutor.execute {
                try {
                    recognize(segment)
                } finally {
                    done.countDown()
                }
            }
        } catch (e: RejectedExecutionException) {
            done.countDown()
            problem("recognition was rejected for the segment at ${seconds(segment)}: ${e.message}")
        }
    }

    private fun recognize(segment: AudioSegment) {
        try {
            val text = recognizer.transcribe(segment).text.trim()
            if (text.isNotEmpty()) append(SpeechEntry(segment.startSeconds.roundToLong(), text))
        } catch (e: Exception) {
            problem("recognition failed for the segment at ${seconds(segment)}: ${e.message ?: e::class.simpleName}")
        }
    }

    private fun append(entry: SessionEntry) {
        synchronized(storeLock) { store.append(entry) }
    }

    private fun finish(failure: Throwable?) {
        var problem: String? = failure?.let { it.message ?: it::class.simpleName.orEmpty() }
        fun attempt(what: String, action: () -> Unit) {
            try {
                action()
            } catch (e: Exception) {
                if (problem == null) problem = "$what failed: ${e.message ?: e::class.simpleName}"
            }
        }
        attempt("stopping the source") { source.stop() }
        attempt("closing the raw audio") { raw.close() }
        awaitRecognitions()
        attempt("closing the session store") { synchronized(storeLock) { store.close() } }
        val message = problem
        transition(if (message == null) PipelineState.Stopped else PipelineState.Error(message))
    }

    private fun awaitRecognitions() {
        for (latch in pending) {
            try {
                latch.await()
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                return
            }
        }
    }

    private fun transition(next: PipelineState) {
        val allowed = when (next) {
            PipelineState.Recording -> state == PipelineState.Idle
            PipelineState.Stopped -> state == PipelineState.Recording
            is PipelineState.Error -> state == PipelineState.Idle || state == PipelineState.Recording
            PipelineState.Idle -> false
        }
        check(allowed) { "invalid pipeline state change from $state to $next" }
        state = next
        runCatching { onState(next) } // a failing observer must not break the capture path
    }

    private fun problem(message: String) {
        runCatching { onProblem(message) }
    }

    private fun seconds(segment: AudioSegment) = String.format(Locale.ROOT, "%.1f s", segment.startSeconds)

    companion object {
        /** 32 ms at 16 kHz, the frame size of the voice detector (STEP-04). */
        const val DEFAULT_FRAME_SAMPLES = 512
    }
}
